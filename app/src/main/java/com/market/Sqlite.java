package com.market;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** نسخة أندرويد من غلاف SQLite: نفس واجهة نسخة الحاسوب (exec / prepare / bind / step / get) فوق مكتبة أندرويد المدمجة */
public final class Sqlite implements AutoCloseable {
    private final SQLiteDatabase db;

    public static boolean available() { return true; }
    public static String version() {
        try (Cursor c = SQLiteDatabase.create(null).rawQuery("select sqlite_version()", null)) { return c.moveToFirst() ? c.getString(0) : "?"; }
        catch (Exception e) { return "?"; }
    }

    public Sqlite(Path file) {
        db = SQLiteDatabase.openDatabase(file.toString(), null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY);
    }

    /** ينفّذ جملة أو عدة جمل مفصولة بـ ; — مع معاملة BEGIN/COMMIT/ROLLBACK وPRAGMA بطريقة أندرويد */
    public void exec(String sql) {
        for (String part : split(sql)) {
            String u = part.trim().toUpperCase();
            if (u.isEmpty()) continue;
            if (u.startsWith("BEGIN")) db.beginTransaction();
            else if (u.startsWith("COMMIT") || u.startsWith("END")) { db.setTransactionSuccessful(); db.endTransaction(); }
            else if (u.startsWith("ROLLBACK")) { if (db.inTransaction()) db.endTransaction(); }
            else if (u.startsWith("PRAGMA JOURNAL_MODE")) { if (!db.inTransaction()) try { db.enableWriteAheadLogging(); } catch (Exception ignored) {} }
            else if (u.startsWith("PRAGMA") || u.startsWith("SELECT")) { try (Cursor c = db.rawQuery(part, null)) { c.moveToFirst(); } }
            else if (u.startsWith("VACUUM")) { if (!db.inTransaction()) db.execSQL(part); }
            else db.execSQL(part);
        }
    }

    static List<String> split(String sql) {
        List<String> out = new ArrayList<>(); StringBuilder cur = new StringBuilder(); boolean q = false;
        for (int i = 0; i < sql.length(); i++) { char c = sql.charAt(i);
            if (c == '\'') q = !q;
            if (c == ';' && !q) { out.add(cur.toString()); cur.setLength(0); } else cur.append(c); }
        if (cur.toString().trim().length() > 0) out.add(cur.toString());
        return out;
    }

    public Stmt prepare(String sql) { return new Stmt(sql); }
    public void close() { try { db.close(); } catch (Exception ignored) {} }

    public final class Stmt implements AutoCloseable {
        final String sql; final boolean query; final List<Object> args = new ArrayList<>();
        SQLiteStatement st; Cursor cur;
        Stmt(String sql) {
            this.sql = sql; String u = sql.trim().toUpperCase();
            query = u.startsWith("SELECT") || u.startsWith("PRAGMA") || u.startsWith("WITH");
            if (!query) st = db.compileStatement(sql);
        }
        public Stmt bind(int i, Object v) {
            while (args.size() < i) args.add(null);
            args.set(i - 1, v);
            if (!query) {
                if (v == null) st.bindNull(i);
                else if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) st.bindLong(i, ((Number) v).longValue());
                else if (v instanceof Boolean) st.bindLong(i, (Boolean) v ? 1 : 0);
                else if (v instanceof Number) st.bindDouble(i, ((Number) v).doubleValue());
                else if (v instanceof byte[]) st.bindBlob(i, (byte[]) v);
                else st.bindString(i, v.toString());
            }
            return this;
        }
        /** للاستعلام: ينتقل للسطر التالي (true إن وُجد). لغيره: ينفّذ الجملة ويرجع false */
        public boolean step() {
            if (!query) { st.execute(); return false; }
            if (cur == null) {
                String[] a = new String[args.size()];
                for (int k = 0; k < a.length; k++) { Object o = args.get(k); a[k] = o == null ? null : o instanceof Boolean ? ((Boolean) o ? "1" : "0") : o.toString(); }
                cur = db.rawQuery(sql, a.length == 0 ? null : a);
            }
            return cur.moveToNext();
        }
        public void run() { step(); reset(); }
        public void reset() {
            if (cur != null) { cur.close(); cur = null; }
            if (st != null) st.clearBindings();
            args.clear();
        }
        public int columns() { return cur == null ? 0 : cur.getColumnCount(); }
        public Object get(int i) {
            switch (cur.getType(i)) {
                case Cursor.FIELD_TYPE_NULL: return null;
                case Cursor.FIELD_TYPE_INTEGER: return cur.getLong(i);
                case Cursor.FIELD_TYPE_FLOAT: return cur.getDouble(i);
                case Cursor.FIELD_TYPE_BLOB: return cur.getBlob(i);
                default: return cur.getString(i);
            }
        }
        public void close() { if (cur != null) cur.close(); if (st != null) st.close(); }
    }
}
