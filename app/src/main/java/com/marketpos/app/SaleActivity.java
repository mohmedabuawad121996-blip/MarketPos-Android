package com.marketpos.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "المبيعات": builds one invoice from typed lines and saves it to the local database. */
public class SaleActivity extends AppCompatActivity {

    private final List<Data.Line> lines = new ArrayList<>();
    private EditText customer;
    private EditText item;
    private EditText qty;
    private EditText price;
    private RadioGroup payGroup;
    private TextView linesView;
    private TextView totalView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "فاتورة مبيعات"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.vertical(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        customer = field("اسم العميل (اختياري)", InputType.TYPE_CLASS_TEXT);
        body.addView(customer);

        payGroup = new RadioGroup(this);
        payGroup.setOrientation(LinearLayout.HORIZONTAL);
        for (String opt : new String[]{"نقداً", "بطاقة", "شيك"}) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(opt);
            rb.setTextColor(Color.WHITE);
            rb.setTag(opt);
            payGroup.addView(rb);
            if (payGroup.getChildCount() == 1) {
                rb.setChecked(true);
            }
        }
        body.addView(payGroup);

        item = field("اسم الصنف", InputType.TYPE_CLASS_TEXT);
        body.addView(item);

        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        qty = field("الكمية", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price = field("السعر", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        nums.addView(qty, weighted());
        nums.addView(price, weighted());
        body.addView(nums);

        body.addView(button("إضافة سطر", v -> addLine()));

        linesView = new TextView(this);
        linesView.setTextColor(Color.WHITE);
        linesView.setTextSize(15);
        linesView.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        body.addView(linesView);

        totalView = new TextView(this);
        totalView.setTextColor(Ui.RED);
        totalView.setTextSize(18);
        body.addView(totalView);

        body.addView(button("حفظ الفاتورة", v -> saveInvoice()));

        setContentView(root);
        refresh();
    }

    private void addLine() {
        String name = item.getText().toString().trim();
        double q = parse(qty.getText().toString());
        double p = parse(price.getText().toString());
        if (name.isEmpty() || q <= 0 || p < 0) {
            Toast.makeText(this, "أدخل اسم الصنف والكمية والسعر", Toast.LENGTH_SHORT).show();
            return;
        }
        lines.add(new Data.Line(name, q, p));
        item.setText("");
        qty.setText("");
        price.setText("");
        refresh();
    }

    private void saveInvoice() {
        if (lines.isEmpty()) {
            Toast.makeText(this, "أضف صنفاً واحداً على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }
        String pay = "نقداً";
        View checked = payGroup.findViewById(payGroup.getCheckedRadioButtonId());
        if (checked != null && checked.getTag() != null) {
            pay = checked.getTag().toString();
        }
        long id = Data.saveSale(this, customer.getText().toString().trim(), pay, lines);
        Toast.makeText(this, "تم حفظ الفاتورة رقم " + id, Toast.LENGTH_SHORT).show();
        lines.clear();
        customer.setText("");
        refresh();
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        double total = 0;
        for (Data.Line l : lines) {
            sb.append(l.name).append("  ×").append(fmt(l.qty)).append("  = ")
                    .append(fmt(l.total())).append('\n');
            total += l.total();
        }
        linesView.setText(sb.length() == 0 ? "لا توجد أصناف بعد" : sb.toString().trim());
        totalView.setText("الإجمالي: " + fmt(total));
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.GRAY);
        e.setTextColor(Color.WHITE);
        e.setBackgroundColor(Ui.CARD);
        e.setInputType(type);
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 10));
        e.setLayoutParams(lp);
        return e;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, Ui.dp(this, 6), Ui.dp(this, 10));
        return lp;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 10));
        b.setLayoutParams(lp);
        return b;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ENGLISH, "%.2f", v);
    }
}
