package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model;
import com.market.Model.Damage;
import com.market.Model.Product;
import com.market.Model.Transfer;
import com.market.Model.Voucher;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** سجلات مع الإلغاء: السندات، المنتجات التالفة، تحويلات المستودعات */
public class LogActivity extends Activity {
    String mode; Ux.Adapter<Object> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getStringExtra("mode");
        LinearLayout body = Ux.screen(this, mode.equals("voucher") ? "السندات (قبض وصرف)" : mode.equals("damage") ? "المنتجات التالفة" : "تحويلات المستودعات", false);
        if (mode.equals("transfer")) body.addView(Ux.button(this, "+ تحويل جديد بين المستودعات", Ux.GREEN, v -> newTransfer()));
        if (mode.equals("damage")) body.addView(Ux.text(this, "لتسجيل تالف: من شاشة الأصناف اضغط على الصنف ← تسجيل تالف", 12, false, Ux.MUTED));
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> cancel(ad.getItem(i)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 8); body.addView(list, lp);
        load();
    }
    void load() {
        List<Object> l = new ArrayList<>();
        List<?> src = mode.equals("voucher") ? Store.db.vouchers : mode.equals("damage") ? Store.db.damages : Store.db.transfers;
        for (int i = src.size() - 1; i >= 0 && l.size() < 400; i--) l.add(src.get(i));
        ad.set(l);
    }
    View row(Object o, int pos) {
        if (o instanceof Voucher) { Voucher v = (Voucher) o;
            return Ux.listRow(this, (v.in ? "سند قبض " : "سند صرف ") + v.no + " — " + Store.db.partyName(v.partyId) + (v.cancelled ? " (ملغى)" : ""),
                    Ui.dt(v.date) + "  •  " + Store.db.walletName(v.walletId) + (v.notes.isEmpty() ? "" : "  •  " + v.notes), Ui.m(v.amount), v.cancelled ? Ux.MUTED : v.in ? Ux.GREEN : Ux.RED); }
        if (o instanceof Damage) { Damage d = (Damage) o; Product p = Store.db.products.get(d.productId);
            return Ux.listRow(this, (p == null ? "#" + d.productId : p.name) + (d.cancelled ? " (ملغى)" : ""), Ui.dt(d.date) + "  •  " + Ui.q(d.qty) + "  •  " + d.reason,
                    Ui.m(d.qty * d.cost), d.cancelled ? Ux.MUTED : Ux.RED); }
        Transfer t = (Transfer) o;
        return Ux.listRow(this, "تحويل " + t.no + ": " + Store.db.whName(t.fromWh) + " ← " + Store.db.whName(t.toWh) + (t.cancelled ? " (ملغى)" : ""),
                Ui.dt(t.date) + (t.notes.isEmpty() ? "" : "  •  " + t.notes), null, 0);
    }
    void cancel(Object o) {
        if (!Session.admin() && !Session.cap("cap:cancel")) { Ux.alert(this, "ليس لديك صلاحية الإلغاء"); return; }
        boolean done = o instanceof Voucher ? ((Voucher) o).cancelled : o instanceof Damage ? ((Damage) o).cancelled : ((Transfer) o).cancelled;
        if (done) return;
        Ux.confirm(this, "إلغاء هذه الحركة وعكس أثرها؟", () -> {
            if (Ux.run(this, () -> { if (o instanceof Voucher) Ops.cancel((Voucher) o); else if (o instanceof Damage) Ops.cancel((Damage) o); else Ops.cancel((Transfer) o); })) load(); });
    }

    void newTransfer() {
        List<Model.Warehouse> ws = Store.db.activeWarehouses();
        if (ws.size() < 2) { Ux.alert(this, "أضف مستودعاً ثانياً أولاً (المستودعات ← إضافة)"); return; }
        List<String> n = new ArrayList<>(); for (Model.Warehouse w : ws) n.add(w.name);
        Ux.choose(this, "من مستودع", n, a -> Ux.choose(this, "إلى مستودع", n, b -> {
            Map<Long, Double> items = new java.util.LinkedHashMap<>(); addItem(ws.get(a).id, ws.get(b).id, items); }));
    }
    void addItem(long from, long to, Map<Long, Double> items) {
        StringBuilder s = new StringBuilder(); for (Map.Entry<Long, Double> e : items.entrySet()) s.append("• ").append(Store.db.products.get(e.getKey()).name).append(": ").append(Ui.q(e.getValue())).append('\n');
        List<String> o = new ArrayList<>(); o.add("+ إضافة صنف"); if (!items.isEmpty()) o.add("✓ تنفيذ التحويل (" + items.size() + " صنف)");
        new android.app.AlertDialog.Builder(this).setTitle(Store.db.whName(from) + " ← " + Store.db.whName(to)).setMessage(s.length() == 0 ? "لا أصناف بعد" : s.toString())
                .setPositiveButton("+ صنف", (d, w) -> Ux.input(this, "ابحث عن الصنف", "الاسم أو الباركود", "", false, q -> {
                    List<Product> ps = new ArrayList<>(); List<String> names = new ArrayList<>(); String k = q.toLowerCase();
                    for (Product x : Store.db.products.values()) if (x.active && (x.name.toLowerCase().contains(k) || (x.barcode != null && x.barcode.contains(k)))) { ps.add(x); names.add(x.name + "  (المتوفر " + Ui.q(Store.db.whQty(x.id, from)) + ")"); if (ps.size() >= 60) break; }
                    if (ps.isEmpty()) { Ux.alert(this, "لا يوجد صنف مطابق"); addItem(from, to, items); return; }
                    Ux.choose(this, "الصنف", names, i -> Ux.input(this, "الكمية بالـ" + ps.get(i).unit, "الكمية", "", true, x -> { double qv = Ui.num(x); if (qv > 0) items.merge(ps.get(i).id, qv, Double::sum); addItem(from, to, items); }));
                }))
                .setNeutralButton(items.isEmpty() ? "إلغاء" : "تنفيذ", (d, w) -> { if (items.isEmpty()) return;
                    if (Ux.run(this, () -> Ops.transfer(from, to, items, ""))) { Ux.toast(this, "تم التحويل"); load(); } })
                .setNegativeButton("إلغاء", null).show();
    }
}
