package com.marketpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model.Offer;
import com.market.Model.Product;
import com.market.Offers;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** العروض والخصومات: تُطبَّق تلقائياً في شاشة البيع */
public class OffersActivity extends Activity {
    Ux.Adapter<Offer> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "العروض والخصومات", false);
        body.addView(Ux.button(this, "+ عرض جديد", Ux.GREEN, v -> edit(null)));
        Ux.add(body, Ux.text(this, "العروض تُطبَّق تلقائياً في فاتورة البيع. الخصم اليدوي على السطر يلغي العرض لذلك السطر.", 12, false, Ux.MUTED), 6);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> actions(ad.getItem(i)));
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        load();
    }
    void load() { List<Offer> l = new ArrayList<>(); for (int i = Store.db.offers.size() - 1; i >= 0; i--) l.add(Store.db.offers.get(i)); ad.set(l); }
    String state(Offer o) { LocalDate t = LocalDate.now(); return !o.active ? "موقوف" : o.liveOn(t) ? "فعّال" : (o.fromDate() != null && t.isBefore(o.fromDate()) ? "لم يبدأ" : "منتهي"); }
    View row(Offer o, int pos) {
        Product p = Store.db.products.get(o.productId); String st = state(o);
        return Ux.listRow(this, p == null ? "(صنف محذوف)" : p.name, Offers.title(o) + "  •  " + o.from + " ← " + o.to, st, st.equals("فعّال") ? Ux.GREEN : Ux.MUTED);
    }
    void actions(Offer o) {
        Ux.choose(this, Offers.title(o), Arrays.asList("تعديل", o.active ? "إيقاف" : "تفعيل", "حذف"), i -> {
            if (i == 0) edit(o);
            else if (i == 1) { o.active = !o.active; Store.save(); load(); }
            else Ux.confirm(this, "حذف العرض؟ (الفواتير السابقة لا تتأثر)", () -> { Store.db.offers.remove(o); Store.save(); load(); });
        });
    }
    void edit(Offer old) {
        Offer o = old == null ? new Offer() : old; final long[] pid = {o.productId}; final int[] kind = {o.kind};
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pd = Ux.dp(this, 18); box.setPadding(pd, Ux.dp(this, 8), pd, 0);
        Product cur = Store.db.products.get(pid[0]);
        Button pb = Ux.button(this, "الصنف: " + (cur == null ? "اختر…" : cur.name), Ux.TEAL, null); box.addView(pb);
        pb.setOnClickListener(v -> Ux.input(this, "ابحث عن الصنف", "الاسم أو الباركود", "", false, q -> {
            List<Product> ps = new ArrayList<>(); List<String> n = new ArrayList<>(); String s = q.toLowerCase();
            for (Product x : Store.db.products.values()) if (x.active && (x.name.toLowerCase().contains(s) || (x.barcode != null && x.barcode.contains(s)))) { ps.add(x); n.add(x.name + "  —  " + Ui.m(x.price)); if (ps.size() >= 60) break; }
            if (ps.isEmpty()) { Ux.alert(this, "لا يوجد صنف مطابق"); return; }
            Ux.choose(this, "الصنف", n, i -> { pid[0] = ps.get(i).id; pb.setText("الصنف: " + ps.get(i).name); });
        }));
        Button kb = Ux.button(this, "النوع: " + Offers.KINDS[kind[0]], Ux.BLUE, null); Ux.add(box, kb, 6);
        kb.setOnClickListener(v -> Ux.choose(this, "نوع العرض", Arrays.asList(Offers.KINDS), i -> { kind[0] = i; kb.setText("النوع: " + Offers.KINDS[i]); }));
        EditText qty = f(box, "الكمية (لعرض «كمية بسعر»)", true, Ui.q(o.qty)), val = f(box, "القيمة (السعر أو النسبة %)", true, o.value == 0 ? "" : Ui.q(o.value)),
                from = f(box, "من تاريخ (yyyy-MM-dd)", false, o.from), to = f(box, "إلى تاريخ (yyyy-MM-dd)", false, o.to), name = f(box, "اسم العرض (اختياري)", false, o.name);
        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(box);
        new AlertDialog.Builder(this).setTitle(old == null ? "عرض جديد" : "تعديل العرض").setView(sv).setPositiveButton("حفظ", (d, w) -> {
            if (pid[0] == 0) { Ux.alert(this, "اختر الصنف"); return; }
            double v = Ui.num(val.getText().toString()); if (v <= 0) { Ux.alert(this, "أدخل قيمة العرض"); return; }
            try { LocalDate.parse(from.getText().toString().trim()); LocalDate.parse(to.getText().toString().trim()); } catch (Exception e) { Ux.alert(this, "صيغة التاريخ غير صحيحة. مثال: 2026-11-17"); return; }
            o.productId = pid[0]; o.kind = kind[0]; o.qty = Math.max(1, Ui.num(qty.getText().toString())); o.value = v;
            o.from = from.getText().toString().trim(); o.to = to.getText().toString().trim(); o.name = name.getText().toString().trim();
            if (old == null) { o.id = Store.db.next("offer"); Store.db.offers.add(o); }
            Store.save(); load();
        }).setNegativeButton("إلغاء", null).show();
    }
    EditText f(LinearLayout box, String label, boolean num, String val) {
        Ux.add(box, Ux.text(this, label, 13, false, Ux.MUTED), 6); EditText e = Ux.field(this, label, num); if (val != null) e.setText(val); box.addView(e); return e;
    }
}
