package com.marketpro.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.market.Model.Category;
import com.market.Model.Product;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;

/** الأصناف والمخزون: بحث، إضافة وتعديل، جرد الكمية، وتسجيل التالف */
public class ProductsActivity extends AppCompatActivity {
    EditText search; TextView sum; long catFilter; int sortMode; android.widget.Button catB; Ux.Adapter<Product> adapter;
    ActivityResultLauncher<ScanOptions> scanner; EditText scanTarget;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        scanner = registerForActivityResult(new ScanContract(), r -> { if (r != null && r.getContents() != null && scanTarget != null) scanTarget.setText(r.getContents()); });
        LinearLayout body = Ux.screen(this, "الأصناف والمخزون", false);
        LinearLayout top = Ux.row(this);
        search = Ux.field(this, "بحث بالاسم أو الباركود", false); top.addView(search, Ux.weight(1));
        Button cam = Ux.button(this, "📷", Ux.BLUE, v -> { scanTarget = search; scan(); });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ux.dp(this, 54), -2); cl.setMarginStart(Ux.dp(this, 6)); top.addView(cam, cl);
        body.addView(top);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable e) { load(); } });
        LinearLayout bar = Ux.row(this);
        bar.addView(Ux.button(this, "+ صنف جديد", Ux.GREEN, v -> edit(null, "")), Ux.weight(1));
        catB = Ux.button(this, "التصنيف: الكل", Ux.TEAL, v -> pickCat()); LinearLayout.LayoutParams cbl = Ux.weight(1); cbl.setMarginStart(Ux.dp(this, 6)); bar.addView(catB, cbl);
        android.widget.Button sortB = Ux.button(this, "ترتيب", Ux.SLATE, null); LinearLayout.LayoutParams sbl = Ux.weight(0.6f); sbl.setMarginStart(Ux.dp(this, 6)); bar.addView(sortB, sbl);
        sortB.setOnClickListener(v -> Ux.choose(this, "ترتيب حسب", java.util.Arrays.asList("الإضافة", "الاسم", "الكمية (الأقل أولاً)", "السعر", "الأكثر قيمة بالمخزون"), i -> { sortMode = i; load(); }));
        Ux.add(body, bar, 6);
        sum = Ux.text(this, "", 13, true, Ux.PRIMARY); Ux.add(body, sum, 6);
        ListView list = new ListView(this); list.setDivider(null);
        adapter = new Ux.Adapter<>(this::row); list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> actions(adapter.getItem(i)));
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        String nc = getIntent().getStringExtra("newCode");
        load();
        if (nc != null) edit(null, nc);
    }

    void scan() { ScanOptions o = new ScanOptions(); o.setPrompt("وجّه الكاميرا إلى الباركود"); o.setBeepEnabled(true); scanner.launch(o); }

    void load() {
        String q = search.getText().toString().trim().toLowerCase();
        List<Product> l = new ArrayList<>(); double cost = 0, sale = 0; int n = 0;
        for (Product p : Store.db.products.values()) {
            if (!p.active) continue;
            if (catFilter != 0 && p.categoryId != catFilter) continue;
            if (p.qty > 0) { cost += p.qty * p.cost; sale += p.qty * p.price; } n++;
            if (!q.isEmpty() && !p.name.toLowerCase().contains(q) && !(p.barcode != null && p.barcode.contains(q))) continue;
            l.add(p);
        }
        if (sortMode == 1) l.sort((a, b) -> a.name.compareTo(b.name)); else if (sortMode == 2) l.sort((a, b) -> Double.compare(a.qty, b.qty));
        else if (sortMode == 3) l.sort((a, b) -> Double.compare(b.price, a.price)); else if (sortMode == 4) l.sort((a, b) -> Double.compare(b.qty * b.cost, a.qty * a.cost));
        if (l.size() > 500) l = new ArrayList<>(l.subList(0, 500));
        adapter.set(l);
        sum.setText("الأصناف: " + n + "  |  المخزون بالتكلفة: " + Ui.m(cost) + "  |  بالبيع: " + Ui.m(sale));
    }

    View row(Product p, int pos) {
        String sub = (p.barcode == null || p.barcode.isEmpty() ? "" : p.barcode + "  •  ") + "الكمية: " + p.stockText()
                + (Session.cap("cap:cost") ? "  •  التكلفة: " + Ui.m(p.cost) : "");
        int c = p.minQty > 0 && p.qty < p.minQty ? Ux.RED : Ux.PRIMARY;
        return Ux.listRow(this, p.name, sub, Ui.m(p.price), c);
    }

    void actions(Product p) {
        List<String> a = new ArrayList<>(); a.add("تعديل الصنف"); a.add("جرد (تعديل الكمية الفعلية)"); a.add("تسجيل تالف");
        a.add("حركة الصنف"); a.add("آخر أسعار الشراء"); a.add("طباعة ملصق باركود"); a.add("حذف الصنف");
        Ux.choose(this, p.name, a, i -> {
            if (i == 3) { int k = com.market.ReportApi.indexOf("حركة صنف"); startActivity(new android.content.Intent(this, ReportViewActivity.class).putExtra("index", k).putExtra("product", p.id)); return; }
            if (i == 4) { history(p); return; }
            if (i == 5) { startActivity(new android.content.Intent(this, LabelsActivity.class).putExtra("product", p.id)); return; }
            if (i == 6) { if (!Session.admin()) { Ux.alert(this, "الحذف للمدير فقط"); return; }
                Ux.confirm(this, "حذف " + p.name + "؟\nإن كان للصنف حركات سيُوقف بدل الحذف.", () -> { final int[][] r = new int[1][]; if (Ux.run(this, () -> r[0] = Ops.deleteProducts(java.util.Collections.singletonList(p.id), true))) { Ux.toast(this, "تم"); load(); } }); return; }
            if (i == 0) edit(p, null);
            else if (i == 1) Ux.input(this, "الكمية الفعلية بالـ" + p.unit + " (الحالية " + Ui.q(p.qty) + ")", "الكمية", Ui.q(p.qty), true,
                    s -> { if (Ux.run(this, () -> Ops.adjust(p, Ui.num(s), "جرد من الهاتف"))) load(); });
            else Ux.input(this, "كمية التالف بالـ" + p.unit, "الكمية", "", true,
                    s -> { if (Ux.run(this, () -> Ops.damage(p, Ui.num(s), "تالف"))) load(); });
        });
    }

    void edit(Product old, String code) {
        boolean isNew = old == null;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pd = Ux.dp(this, 18); box.setPadding(pd, Ux.dp(this, 8), pd, 0);
        EditText name = f(box, "اسم الصنف", false, isNew ? "" : old.name);
        LinearLayout bc = Ux.row(this); EditText barcode = Ux.field(this, "الباركود", false); barcode.setText(isNew ? code : old.barcode);
        bc.addView(barcode, Ux.weight(1)); Button cam = Ux.button(this, "📷", Ux.BLUE, v -> { scanTarget = barcode; scan(); });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ux.dp(this, 50), -2); cl.setMarginStart(Ux.dp(this, 6)); bc.addView(cam, cl);
        box.addView(Ux.text(this, "الباركود", 13, false, Ux.MUTED)); box.addView(bc);
        final long[] cat = {isNew ? (Store.db.categories.isEmpty() ? 0 : Store.db.categories.get(0).id) : old.categoryId};
        Button catB = Ux.button(this, "التصنيف: " + Store.db.categoryName(cat[0]), Ux.TEAL, null); Ux.add(box, catB, 8);
        catB.setOnClickListener(v -> { List<String> n = new ArrayList<>(); for (Category c : Store.db.categories) n.add(c.name);
            Ux.choose(this, "التصنيف", n, i -> { cat[0] = Store.db.categories.get(i).id; catB.setText("التصنيف: " + Store.db.categories.get(i).name); }); });
        EditText unit = f(box, "الوحدة", false, isNew ? "حبة" : old.unit);
        EditText cost = f(box, "التكلفة (للحبة)", true, isNew ? "" : String.valueOf(old.cost));
        EditText price = f(box, "سعر البيع", true, isNew ? "" : String.valueOf(old.price));
        EditText min = f(box, "الحد الأدنى للتنبيه", true, isNew || old.minQty == 0 ? "" : Ui.q(old.minQty));
        EditText bigU = f(box, "الوحدة الكبرى (كرتونة/باكيت) — اختياري", false, isNew ? "" : old.bigUnit);
        EditText factor = f(box, "عدد الحبات في الوحدة الكبرى", true, isNew || old.factor <= 1 ? "" : Ui.q(old.factor));
        EditText bigP = f(box, "سعر بيع الوحدة الكبرى", true, isNew || old.bigPrice == 0 ? "" : String.valueOf(old.bigPrice));
        EditText bigC = f(box, "باركود الوحدة الكبرى", false, isNew ? "" : old.bigBarcode);
        EditText extra = f(box, "باركودات إضافية (مفصولة بفاصلة)", false, isNew ? "" : old.extraBarcodes);
        EditText plu = f(box, "رقم الصنف في الميزان (PLU)", false, isNew ? "" : old.plu);
        android.widget.CheckBox weighed = new android.widget.CheckBox(this); weighed.setText("يُباع بالوزن (كغ)"); weighed.setChecked(!isNew && old.weighed); box.addView(weighed);
        android.widget.CheckBox taxable = new android.widget.CheckBox(this); taxable.setText("خاضع للضريبة"); taxable.setChecked(isNew || old.taxable); box.addView(taxable);
        EditText open = isNew ? f(box, "الرصيد الافتتاحي (الكمية)", true, "") : null;
        ScrollView sv = new ScrollView(this); sv.addView(box);
        new Dlg(this).setTitle(isNew ? "صنف جديد" : "تعديل " + old.name).setView(sv)
                .setPositiveButton("حفظ", (d, w) -> {
                    Product p = isNew ? new Product() : old;
                    String bcode = barcode.getText().toString().trim(), bigCode = bigC.getText().toString().trim();
                    if (!isNew) { Product o1 = bcode.isEmpty() ? null : Store.db.byBarcode(bcode), o2 = bigCode.isEmpty() ? null : Store.db.byBarcode(bigCode);
                        if ((o1 != null && o1 != p) || (o2 != null && o2 != p)) { Ux.alert(this, "الباركود مستخدم لصنف آخر"); return; }
                        if (name.getText().toString().trim().isEmpty()) { Ux.alert(this, "اسم الصنف مطلوب"); return; } }
                    p.name = name.getText().toString().trim(); p.barcode = bcode; p.categoryId = cat[0];
                    p.unit = unit.getText().toString().trim().isEmpty() ? "حبة" : unit.getText().toString().trim();
                    p.cost = Ui.num(cost.getText().toString()); p.price = Ui.num(price.getText().toString()); p.minQty = Ui.num(min.getText().toString());
                    p.bigUnit = bigU.getText().toString().trim(); double fct = Ui.num(factor.getText().toString()); p.factor = fct > 1 ? fct : 1;
                    p.bigPrice = Ui.num(bigP.getText().toString()); p.bigBarcode = bigCode;
                    p.extraBarcodes = extra.getText().toString().trim(); p.plu = plu.getText().toString().trim(); p.weighed = weighed.isChecked(); p.taxable = taxable.isChecked();
                    if (isNew) { double q = Ui.num(open.getText().toString()); if (Ux.run(this, () -> Ops.addProduct(p, q))) { Ux.toast(this, "تمت إضافة الصنف"); load(); } }
                    else { Store.db.reindex(); Store.save(); load(); }
                }).setNegativeButton("إلغاء", null).show();
    }
    void pickCat() {
        List<Category> cs = Store.db.categories; List<String> n = new ArrayList<>(); n.add("— الكل —"); for (Category c : cs) n.add(c.name);
        Ux.choose(this, "التصنيف", n, i -> { catFilter = i == 0 ? 0 : cs.get(i - 1).id; catB.setText("التصنيف: " + (i == 0 ? "الكل" : n.get(i))); load(); });
    }
    void history(Product p) {
        List<com.market.Model.Invoice> inv = new ArrayList<>(); for (com.market.Model.Invoice i : Store.db.invoices) if (!i.cancelled && i.type == com.market.Model.InvType.PURCHASE) inv.add(i);
        inv.sort((a, b) -> b.date.compareTo(a.date)); StringBuilder sb = new StringBuilder(); int n = 0;
        outer: for (com.market.Model.Invoice i : inv) for (com.market.Model.InvLine l : i.lines) { if (l.productId != p.id || l.qty <= 0) continue;
            sb.append(Ui.dt(i.date)).append("  •  ").append(Store.db.partyName(i.partyId)).append("\n    ").append(Ui.m(l.total() / l.qty)).append(" لكل ").append(l.unit).append("  (").append(Ui.q(l.qty)).append(")\n");
            if (++n >= 10) break outer; }
        Ux.alert(this, n == 0 ? "لا توجد مشتريات سابقة لهذا الصنف" : p.name + " — آخر " + n + " أسعار شراء:\n\n" + sb);
    }
    EditText f(LinearLayout box, String label, boolean num, String val) {
        box.addView(Ux.text(this, label, 13, false, Ux.MUTED));
        EditText e = Ux.field(this, label, num); if (val != null) e.setText(val); box.addView(e); return e;
    }
}
