package com.marketpro.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.market.Expiry;
import com.market.Model.Category;
import com.market.Model.InvLine;
import com.market.Model.InvType;
import com.market.Model.Party;
import com.market.Model.Product;
import com.market.Offers;
import com.market.Ops;
import com.market.PurchaseOrders;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** أدوات المخزون والمعالجات المتقدمة */
public class ToolsActivity extends AppCompatActivity {
    ActivityResultLauncher<ScanOptions> scanner;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        scanner = registerForActivityResult(new ScanContract(), r -> { if (r != null && r.getContents() != null) priceOf(r.getContents()); });
        LinearLayout body = Ux.screen(this, "أدوات ومعالجات", true);
        boolean admin = Session.admin();
        sec(body, "المخزون");
        item(body, "استعلام عن سعر (بالكاميرا)", Ux.BLUE, () -> { ScanOptions o = new ScanOptions(); o.setPrompt("وجّه الكاميرا إلى الباركود"); o.setBeepEnabled(true); scanner.launch(o); });
        item(body, "استعلام عن سعر (بالاسم)", Ux.BLUE, () -> Ux.input(this, "اسم الصنف أو الباركود", "بحث", "", false, this::priceOf));
        item(body, "تواريخ الصلاحية", Ux.ORANGE, this::expiry);
        item(body, "اقتراح طلبية (حسب البيع والحد الأدنى)", Ux.GREEN, this::suggest);
        item(body, "المنتجات التالفة", Ux.RED, () -> startActivity(new Intent(this, LogActivity.class).putExtra("mode", "damage")));
        item(body, "المستودعات", Ux.TEAL, () -> startActivity(new Intent(this, ListsActivity.class).putExtra("mode", "wh")));
        item(body, "تحويل بين المستودعات", Ux.TEAL, () -> startActivity(new Intent(this, LogActivity.class).putExtra("mode", "transfer")));
        item(body, "التصنيفات", Ux.PURPLE, () -> startActivity(new Intent(this, ListsActivity.class).putExtra("mode", "cat")));
        if (admin) item(body, "تعديل أسعار المنتجات (نسبة أو هامش)", Ux.PURPLE, this::bulkPrice);
        sec(body, "الحسابات");
        item(body, "السندات وإلغاؤها", Ux.DARK, () -> startActivity(new Intent(this, LogActivity.class).putExtra("mode", "voucher")));
        item(body, "إدارة المحافظ", Ux.DARK, () -> startActivity(new Intent(this, ListsActivity.class).putExtra("mode", "wallet")));
        item(body, "بنود المصروفات", Ux.DARK, () -> startActivity(new Intent(this, ListsActivity.class).putExtra("mode", "expcat")));
        if (admin) {
            sec(body, "معالجة البيانات (للمدير)");
            item(body, "فحص سلامة البيانات", Ux.BLUE, () -> { List<String> r = Ops.integrityCheck();
                Ux.alert(this, r.isEmpty() ? "✓ البيانات سليمة ولا توجد مشاكل" : "تم العثور على " + r.size() + " ملاحظة:\n\n" + String.join("\n", r.subList(0, Math.min(60, r.size())))); });
            item(body, "إعادة احتساب كميات المخزون من الحركات", Ux.BLUE, () -> Ux.confirm(this, "إعادة احتساب كمية كل صنف من حركات المخزون؟", () -> {
                final int[] n = new int[1]; if (Ux.run(this, () -> n[0] = Ops.recalcStock())) Ux.alert(this, "تم. عدد الأصناف التي صُحّحت: " + n[0]); }));
            item(body, "حذف كل الحركات (إبقاء الأصناف والحسابات)", Ux.RED, () -> danger("سيُحذف كل شيء من فواتير وسندات ومصروفات وحركات، وتُصفّر الكميات. تُحفظ نسخة احتياطية أولاً.", () -> Ops.wipeTransactions()));
            item(body, "تصفير البرنامج لبداية جديدة", Ux.RED, () -> danger("تصفير كامل: الحركات والأرصدة الافتتاحية. تُحفظ نسخة احتياطية أولاً.", () -> Ops.resetProgram(true)));
        }
        String d = getIntent().getStringExtra("do");
        if ("price".equals(d)) { ScanOptions o = new ScanOptions(); o.setPrompt("وجّه الكاميرا إلى الباركود (أو ارجع للبحث بالاسم)"); o.setBeepEnabled(true); scanner.launch(o); }
        else if ("expiry".equals(d)) expiry(); else if ("suggest".equals(d)) suggest(); else if ("bulk".equals(d)) bulkPrice();
    }
    void sec(LinearLayout body, String t) { Ux.add(body, Ux.text(this, t, 16, true, Ux.TEXT), 14); }
    void item(LinearLayout body, String t, int c, Runnable r) { Ux.add(body, Ux.button(this, t, c, v -> r.run()), 6); }

    void danger(String msg, Runnable op) {
        Ux.confirm(this, msg + "\n\nهل أنت متأكد؟", () -> Ux.input(this, "للتأكيد اكتب: نعم احذف", "نعم احذف", "", false, s -> {
            if (!s.trim().equals("نعم احذف")) { Ux.alert(this, "لم يتم التنفيذ"); return; }
            if (Ux.run(this, () -> { try { Store.backupTo(Store.dataDir.resolve("backups"), "before_wipe"); } catch (Exception e) { throw new RuntimeException("تعذّر حفظ نسخة احتياطية: " + e.getMessage()); } op.run(); }))
                Ux.alert(this, "تم التنفيذ. النسخة السابقة محفوظة داخل التطبيق."); }));
    }

    void priceOf(String q) {
        Product p = Store.db.byBarcode(q.trim());
        if (p == null) { List<Product> ps = new ArrayList<>(); List<String> n = new ArrayList<>(); String k = q.trim().toLowerCase();
            for (Product x : Store.db.products.values()) if (x.active && x.name.toLowerCase().contains(k)) { ps.add(x); n.add(x.name); if (ps.size() >= 60) break; }
            if (ps.isEmpty()) { Ux.alert(this, "الصنف غير موجود: " + q); return; }
            if (ps.size() > 1) { Ux.choose(this, "اختر الصنف", n, i -> showPrice(ps.get(i))); return; }
            p = ps.get(0); }
        showPrice(p);
    }
    void showPrice(Product p) {
        String offer = ""; com.market.Model.Offer o = Offers.live(p.id, java.time.LocalDate.now()); if (o != null) offer = "\n\n🏷 عرض: " + Offers.title(o);
        String msg = "سعر " + p.unit + ": " + Ui.m(p.price) + (p.hasBig() ? "\nسعر " + p.bigUnit + " (" + Ui.q(p.factor) + "): " + Ui.m(p.bigPrice > 0 ? p.bigPrice : p.price * p.factor) : "")
                + "\n\nالمتوفر: " + p.stockText() + (Session.cap("cap:cost") ? "\nالتكلفة: " + Ui.m(p.cost) : "") + offer;
        new Dlg(this).setTitle(p.name).setMessage(msg).setPositiveButton("إغلاق", null).show();
    }

    void expiry() {
        List<Expiry.Row> rows = Expiry.open(); if (rows.isEmpty()) { Ux.alert(this, "لا توجد أصناف بتواريخ صلاحية مسجّلة.\nسجّل تاريخ الصلاحية في فواتير المشتريات من برنامج الكمبيوتر."); return; }
        StringBuilder s = new StringBuilder(); int days = Expiry.alertDays();
        for (Expiry.Row r : rows) s.append(r.days < 0 ? "⛔ " : r.days <= days ? "⚠ " : "• ").append(r.p.name).append("\n    ").append(r.b.expiry).append("  •  ")
                .append(r.days < 0 ? "منتهي منذ " + (-r.days) + " يوم" : "باقٍ " + r.days + " يوم").append("  •  الكمية ").append(Ui.q(r.left)).append('\n');
        new Dlg(this).setTitle("تواريخ الصلاحية").setMessage(s.toString()).setPositiveButton("إغلاق", null)
                .setNeutralButton("مشاركة", (d, w) -> Share.text(this, "تواريخ الصلاحية", s.toString())).show();
    }

    void suggest() {
        List<Party> sup = Store.db.parties(com.market.Model.SUPPLIER); List<String> n = new ArrayList<>(); n.add("— كل الموردين —"); for (Party x : sup) n.add(x.name);
        Ux.choose(this, "المورد", n, i -> {
            long sid = i == 0 ? 0 : sup.get(i - 1).id;
            List<PurchaseOrders.Sug> l = PurchaseOrders.suggest(sid, 30, 14);
            if (l.isEmpty()) { Ux.alert(this, "لا توجد أصناف تحتاج طلب حالياً"); return; }
            StringBuilder s = new StringBuilder(); double tot = 0;
            for (PurchaseOrders.Sug g : l) { s.append("• ").append(g.p.name).append(": ").append(PurchaseOrders.qtyText(g.p, g.need)).append("  (المتوفر ").append(Ui.q(g.p.qty)).append(")\n"); tot += g.need * g.cost; }
            s.append("\nالقيمة التقديرية: ").append(Ui.m(tot)).append("\n(حسب مبيعات آخر 30 يوماً لتغطية 14 يوماً)");
            new Dlg(this).setTitle("اقتراح طلبية — " + l.size() + " صنف").setMessage(s.toString()).setNegativeButton("إغلاق", null)
                    .setNeutralButton("مشاركة", (d, w) -> Share.text(this, "اقتراح طلبية", s.toString()))
                    .setPositiveButton("إنشاء طلبية شراء", (d, w) -> {
                        com.market.Model.Invoice o = new com.market.Model.Invoice(); o.type = InvType.ORDER; o.partyId = sid; o.user = Session.name();
                        for (PurchaseOrders.Sug g : l) { InvLine x = new InvLine(); x.productId = g.p.id; x.name = g.p.name; x.barcode = g.p.barcode; x.unit = g.p.unit; x.factor = 1; x.qty = g.need; x.price = g.p.cost; x.cost = g.p.cost; x.taxable = g.p.taxable; o.lines.add(x); }
                        final com.market.Model.Invoice[] out = new com.market.Model.Invoice[1];
                        if (Ux.run(this, () -> { Ops.compute(o); out[0] = Ops.save(o); })) Ux.alert(this, "تم حفظ طلبية الشراء رقم " + out[0].no + "\nتجدها في المشتريات ← سجل الطلبيات");
                    }).show();
        });
    }

    void bulkPrice() {
        List<Category> cs = Store.db.categories; List<String> n = new ArrayList<>(); n.add("— كل الأصناف —"); for (Category c : cs) n.add(c.name);
        Ux.choose(this, "الأصناف", n, ci -> Ux.choose(this, "طريقة التعديل", Arrays.asList("رفع/خفض سعر البيع بنسبة %", "سعر البيع = التكلفة + هامش %"), mode ->
                Ux.input(this, mode == 0 ? "النسبة % (سالبة للتخفيض)" : "هامش الربح %", "النسبة", "", true, s -> {
                    double pct = Ui.num(s); long cat = ci == 0 ? 0 : cs.get(ci - 1).id; int cnt = 0;
                    for (Product p : Store.db.products.values()) if (p.active && (cat == 0 || p.categoryId == cat)) cnt++;
                    int count = cnt;
                    Ux.confirm(this, "تعديل أسعار " + count + " صنف؟", () -> {
                        for (Product p : Store.db.products.values()) { if (!p.active || (cat != 0 && p.categoryId != cat)) continue;
                            double np = mode == 0 ? p.price * (1 + pct / 100) : p.cost * (1 + pct / 100); if (np <= 0) continue;
                            double ratio = p.price > 0 ? np / p.price : 0; p.price = Ui.r(np); if (p.bigPrice > 0 && ratio > 0) p.bigPrice = Ui.r(p.bigPrice * ratio); }
                        Store.save(); Ux.alert(this, "تم تعديل أسعار " + count + " صنف");
                    });
                })));
    }
}
