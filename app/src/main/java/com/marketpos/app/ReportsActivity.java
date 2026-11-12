package com.marketpos.app;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** "الاستعلامات" screen: date range at the top, then grouped report rows. */
public class ReportsActivity extends AppCompatActivity {

    /** The only report that reads real data so far (sales invoices from the local database). */
    private static final String SALES_TITLE = "تقرير بالمبيعات";

    private final Calendar from = Calendar.getInstance();
    private final Calendar to = Calendar.getInstance();
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        from.set(from.get(Calendar.YEAR), Calendar.JANUARY, 1);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "الاستعلامات"));
        root.addView(filterBar());

        LinearLayout list = Ui.vertical(this);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        // Store
        section(list, "المتجر");
        plainRow(list, "عرض حركة المتجر");
        plainRow(list, "عرض حركة المتجر - رسم بياني");

        // Sales
        section(list, "المبيعات");
        plainRow(list, "تقرير بالمبيعات");
        plainRow(list, "تقارير الارباح");
        plainRow(list, "عرض فواتير المبيعات");
        plainRow(list, "تقرير بالخصومات");
        plainRow(list, "تقرير بالفواتير الآجل");
        plainRow(list, "تقرير بالفواتير المرتجع-مبيعات");
        plainRow(list, "تقرير بفواتير المبيعات التي تم الغائها");

        // Customers
        section(list, "العملاء");
        plainRow(list, "ذمم العملاء");
        switchRow(list, "كشف حساب عميل",
                "كشف تفصيلي", "اخفاء عمود الرصيد", "اخفاء رصيد ماقبل الفترة", "اظهار الفواتير النقد");
        plainRow(list, "تقرير مصادقة حساب العميل");
        plainRow(list, "تقرير بحركه الرصيد الافتتاحي والنقد للعميل");
        plainRow(list, "تقرير بالفواتير لعميل");

        // Suppliers
        section(list, "الموردين");
        plainRow(list, "تقرير بالمتبقي للموردين");
        switchRow(list, "كشف حساب مورد",
                "اخفاء عمود الرصيد", "اخفاء رصيد ماقبل الفترة", "اظهار الفواتير النقد");
        plainRow(list, "تقرير بحركه الرصيد الافتتاحي والنقد للمورد");
        plainRow(list, "تقرير بالفواتير لمورد");
        plainRow(list, "تقرير بسندات الصرف لمورد");
        plainRow(list, "تقرير بسندات القبض لمورد");

        // Cash box
        section(list, "الصندوق");
        radioRow(list, "نقداً", "بطاقة", "شيك");
        switchRow(list, "تقرير بحركة الصندوق",
                "اظهار حركات الاضافة", "اظهار حركات الخصم", "اظهار الحركات من شاشة الصندوق");
        plainRow(list, "تقرير رأس المال");
        plainRow(list, "حساب الزكاة");
        plainRow(list, "تقرير بالاقرار الضريبي");

        // Expenses
        section(list, "المصروفات");
        plainRow(list, "تقرير بالمصروفات");
        plainRow(list, "تقرير بالمصروفات حسب الحساب");
        plainRow(list, "تقرير بالمصروفات حسب البيان");
        plainRow(list, "تقرير بالمصروفات حسب طريقة الدفع");

        // Inventory
        section(list, "المخازن");
        switchRow(list, "جرد مخزني", "اظهار الكميات المتوفره");
        plainRow(list, "جرد مخزني حسب التصنيف");
        plainRow(list, "تقرير بالمنتجات حسب تاريخ الانتهاء");
        plainRow(list, "تقرير بحركه منتج");
        plainRow(list, "تقرير بالمنتجات التالفة");
    }

    // ---------- top filter bar ----------

    private LinearLayout filterBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));

        // Added left-to-right in code; RTL layout shows them right-to-left.
        bar.addView(label("للفترة من"));
        bar.addView(dateButton(from));
        bar.addView(label("الي"));
        bar.addView(dateButton(to));
        bar.addView(label("💾"));
        return bar;
    }

    private TextView dateButton(final Calendar cal) {
        final TextView b = new TextView(this);
        b.setText(fmt.format(cal.getTime()));
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.parseColor("#4A4D52"));
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        b.setLayoutParams(lp);

        b.setOnClickListener(v -> new DatePickerDialog(this, (picker, y, m, d) -> {
            cal.set(y, m, d);
            b.setText(fmt.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show());
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        return t;
    }

    // ---------- list building blocks ----------

    private void section(LinearLayout list, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Ui.RED);
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        t.setBackgroundColor(Ui.SECTION);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        list.addView(t, matchWrap());
        list.addView(divider());
    }

    private void plainRow(LinearLayout list, final String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Ui.CARD);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("‹");
        arrow.setTextColor(Ui.ACCENT);
        arrow.setTextSize(26);

        row.addView(name);
        row.addView(cloudButton(title));
        row.addView(arrow);
        row.setOnClickListener(v -> onReportClick(title));

        list.addView(row, matchWrap());
        list.addView(divider());
    }

    /** A report row with on/off switches under its title. */
    private void switchRow(LinearLayout list, String title, String... switches) {
        LinearLayout box = Ui.vertical(this);
        box.setBackgroundColor(Ui.CARD);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        head.addView(t);
        head.addView(cloudButton(title));
        box.addView(head);

        for (String s : switches) {
            Switch sw = new Switch(this);
            sw.setText(s);
            sw.setTextColor(Color.LTGRAY);
            sw.setTextSize(14);
            sw.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), 0, Ui.dp(this, 6));
            box.addView(sw);
        }

        list.addView(box, matchWrap());
        list.addView(divider());
    }

    /** A single-choice row (for example payment method). */
    private void radioRow(LinearLayout list, String... options) {
        LinearLayout box = Ui.vertical(this);
        box.setBackgroundColor(Ui.CARD);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(options[i]);
            rb.setTextColor(Color.WHITE);
            rb.setChecked(i == 0);
            group.addView(rb);
        }
        box.addView(group);

        list.addView(box, matchWrap());
        list.addView(divider());
    }

    /** Tap on a row: sales show a summary from the database; other reports show a notice. */
    private void onReportClick(String title) {
        if (!SALES_TITLE.equals(title)) {
            Toast.makeText(this, title + " - قريباً", Toast.LENGTH_SHORT).show();
            return;
        }
        double[] s = Data.salesSummary(this, startMillis(), endMillis());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("عدد الفواتير: " + (int) s[0]
                        + "\nالإجمالي: " + String.format(Locale.ENGLISH, "%.2f", s[1]))
                .setPositiveButton("حسناً", null)
                .show();
    }

    /** Builds the CSV for a report: sales rows come from the database, others only the header. */
    private File buildCsv(String title) throws IOException {
        String f = fmt.format(from.getTime());
        String t = fmt.format(to.getTime());
        if (SALES_TITLE.equals(title)) {
            return ReportExport.writeCsv(this, title, f, t,
                    Data.SALES_HEADER, Data.salesRows(this, startMillis(), endMillis()));
        }
        return ReportExport.writeCsv(this, title, f, t,
                new String[]{"التقرير", "من", "إلى"},
                Collections.singletonList(new String[]{title, f, t}));
    }

    /** Start of the "from" day in millis. */
    private long startMillis() {
        Calendar c = (Calendar) from.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** End of the "to" day in millis. */
    private long endMillis() {
        Calendar c = (Calendar) to.clone();
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }

    /** Cloud icon on each report row: saves that report as CSV to Google Drive. */
    private TextView cloudButton(final String title) {
        TextView c = new TextView(this);
        c.setText("☁");
        c.setTextColor(Color.parseColor("#7FB8D8"));
        c.setTextSize(20);
        c.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        c.setOnClickListener(v -> exportToDrive(title));
        return c;
    }

    private void exportToDrive(String title) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "سجّل الدخول إلى Google Drive من شاشة النسخ الاحتياطي أولاً",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            File csv = buildCsv(title);
            DriveHelper.upload(this, account, csv, "text/csv");
        } catch (IOException e) {
            Toast.makeText(this, "تعذر إنشاء الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
