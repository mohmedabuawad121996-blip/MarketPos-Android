package com.marketpos.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Local data layer (SQLite file "marketpos.db"). Sales are stored as invoices with lines.
 * The backup screen copies this same file to Google Drive.
 */
final class Data {

    static final String DB_NAME = "marketpos.db";
    static final String[] SALES_HEADER = {"رقم الفاتورة", "التاريخ", "العميل", "طريقة الدفع", "الإجمالي"};

    private static DbHelper helper;

    private Data() {
    }

    /** One line of a sale: free-text item name, quantity and unit price. */
    static final class Line {
        final String name;
        final double qty;
        final double price;

        Line(String name, double qty, double price) {
            this.name = name;
            this.qty = qty;
            this.price = price;
        }

        double total() {
            return qty * price;
        }
    }

    private static final class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context c) {
            super(c, DB_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE invoices ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "created INTEGER NOT NULL, "
                    + "customer TEXT, "
                    + "pay_type TEXT NOT NULL, "
                    + "total REAL NOT NULL)");
            db.execSQL("CREATE TABLE invoice_lines ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "invoice_id INTEGER NOT NULL, "
                    + "item_name TEXT NOT NULL, "
                    + "qty REAL NOT NULL, "
                    + "price REAL NOT NULL)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Version 1 is the first schema; add migrations here later.
        }
    }

    private static synchronized SQLiteDatabase db(Context c) {
        if (helper == null) {
            helper = new DbHelper(c.getApplicationContext());
        }
        return helper.getWritableDatabase();
    }

    /** Saves a sale and its lines in one transaction. Returns the invoice id. */
    static long saveSale(Context c, String customer, String payType, List<Line> lines) {
        SQLiteDatabase db = db(c);
        db.beginTransaction();
        try {
            double total = 0;
            for (Line l : lines) {
                total += l.total();
            }

            ContentValues inv = new ContentValues();
            inv.put("created", System.currentTimeMillis());
            inv.put("customer", customer);
            inv.put("pay_type", payType);
            inv.put("total", total);
            long id = db.insertOrThrow("invoices", null, inv);

            for (Line l : lines) {
                ContentValues row = new ContentValues();
                row.put("invoice_id", id);
                row.put("item_name", l.name);
                row.put("qty", l.qty);
                row.put("price", l.price);
                db.insertOrThrow("invoice_lines", null, row);
            }

            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    /** Sales invoices created between two times (millis), as rows matching SALES_HEADER. */
    static List<String[]> salesRows(Context c, long fromMillis, long toMillis) {
        List<String[]> rows = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ENGLISH);
        Cursor cur = db(c).rawQuery(
                "SELECT id, created, customer, pay_type, total FROM invoices "
                        + "WHERE created BETWEEN ? AND ? ORDER BY created",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)});
        try {
            while (cur.moveToNext()) {
                String customer = cur.isNull(2) ? "" : cur.getString(2);
                rows.add(new String[]{
                        String.valueOf(cur.getLong(0)),
                        f.format(new Date(cur.getLong(1))),
                        customer,
                        cur.getString(3),
                        String.valueOf(cur.getDouble(4))});
            }
        } finally {
            cur.close();
        }
        return rows;
    }

    /** Count and total of sales between two times, for a quick summary. */
    static double[] salesSummary(Context c, long fromMillis, long toMillis) {
        Cursor cur = db(c).rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM invoices WHERE created BETWEEN ? AND ?",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)});
        try {
            cur.moveToFirst();
            return new double[]{cur.getDouble(0), cur.getDouble(1)};
        } finally {
            cur.close();
        }
    }
}
