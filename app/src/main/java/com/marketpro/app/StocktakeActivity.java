package com.marketpro.app;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.market.Model.Product;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;

/** الجرد وتسوية الكميات: امسح الصنف واكتب الكمية الفعلية */
public class StocktakeActivity extends AppCompatActivity {
    final List<String[]> done = new ArrayList<>(); Ux.Adapter<String[]> ad; EditText code;
    ActivityResultLauncher<ScanOptions> scanner;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        scanner = registerForActivityResult(new ScanContract(), r -> { if (r != null && r.getContents() != null) find(r.getContents()); });
        LinearLayout body = Ux.screen(this, "الجرد وتسوية الكميات", false);
        body.addView(Ux.text(this, "امسح باركود الصنف أو اكتب اسمه، ثم أدخل الكمية الموجودة فعلياً على الرف. الفرق يُسجَّل كتسوية جرد.", 12, false, Ux.MUTED));
        LinearLayout r = Ux.row(this);
        code = Ux.field(this, "الباركود أو الاسم", false); code.setImeOptions(EditorInfo.IME_ACTION_DONE);
        code.setOnEditorActionListener((v, id, e) -> { find(code.getText().toString()); return true; });
        r.addView(code, Ux.weight(1));
        android.widget.Button cam = Ux.button(this, "📷", Ux.BLUE, v -> { ScanOptions o = new ScanOptions(); o.setPrompt("وجّه الكاميرا إلى الباركود"); o.setBeepEnabled(true); scanner.launch(o); });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ux.dp(this, 54), -2); cl.setMarginStart(Ux.dp(this, 6)); r.addView(cam, cl);
        Ux.add(body, r, 6);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>((x, i) -> Ux.listRow(this, x[0], "كانت " + x[1] + "  ←  أصبحت " + x[2], x[3], x[3].startsWith("-") ? Ux.RED : Ux.GREEN)); list.setAdapter(ad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 8); body.addView(list, lp);
    }
    void find(String q) {
        q = q.trim(); if (q.isEmpty()) return;
        Product p = Store.db.byBarcode(q);
        if (p != null) { ask(p); return; }
        List<Product> ps = new ArrayList<>(); List<String> n = new ArrayList<>(); String k = q.toLowerCase();
        for (Product x : Store.db.products.values()) if (x.active && x.name.toLowerCase().contains(k)) { ps.add(x); n.add(x.name + "  (" + x.stockText() + ")"); if (ps.size() >= 60) break; }
        if (ps.isEmpty()) { Ux.alert(this, "الصنف غير موجود: " + q); return; }
        if (ps.size() == 1) ask(ps.get(0)); else Ux.choose(this, "اختر الصنف", n, i -> ask(ps.get(i)));
    }
    void ask(Product p) {
        Ux.input(this, p.name + "\nالكمية في البرنامج: " + p.stockText() + "\nالكمية الفعلية بالـ" + p.unit + ":", "الكمية الفعلية", "", true, s -> {
            if (s.trim().isEmpty()) return; double before = p.qty, actual = Ui.num(s);
            if (Ux.run(this, () -> Ops.adjust(p, actual, "جرد"))) {
                double diff = Ui.r(actual - before, 3);
                done.add(0, new String[]{p.name, Ui.q(before), Ui.q(actual), (diff > 0 ? "+" : "") + Ui.q(diff)}); ad.set(done); code.setText(""); code.requestFocus();
            }
        });
    }
}
