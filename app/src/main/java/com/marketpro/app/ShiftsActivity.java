package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model.Shift;
import com.market.Session;
import com.market.Shifts;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;

/** الورديات وإغلاق الصندوق: فتح وردية، أرقامها لحظياً، الإغلاق بجرد الدرج، والسجل */
public class ShiftsActivity extends Activity {
    LinearLayout card; Ux.Adapter<Shift> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "الورديات وإغلاق الصندوق", false);
        card = Ux.card(this); body.addView(card);
        Ux.add(body, Ux.text(this, "سجل الورديات", 15, true, Ux.TEXT), 10);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> show(ad.getItem(i)));
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        load();
    }
    void load() {
        card.removeAllViews();
        Shift s = Shifts.current();
        if (s == null) {
            card.addView(Ux.text(this, "لا توجد وردية مفتوحة لـ " + Session.name(), 15, true, Ux.MUTED));
            Ux.add(card, Ux.button(this, "فتح وردية جديدة", Ux.GREEN, v -> Ux.input(this, "المبلغ الموجود في الدرج الآن (فكّة البداية)", "المبلغ", Ui.q(Shifts.lastFloat()), true,
                    x -> { if (Ux.run(this, () -> Shifts.open(Ui.num(x)))) load(); })), 8);
        } else {
            Shifts.compute(s);
            card.addView(Ux.text(this, "وردية مفتوحة رقم " + s.no + " منذ " + Ui.dt(s.opened), 15, true, Ux.PRIMARY));
            stats(card, s);
            Ux.add(card, Ux.button(this, "إغلاق الوردية وجرد الدرج", Ux.RED, v -> Ux.input(this, "المبلغ الموجود فعلياً في الدرج (المتوقع " + Ui.m(s.expectedCash) + ")", "المبلغ المعدود", "", true,
                    x -> Ux.input(this, "ملاحظات (اختياري)", "ملاحظات", "", false, n -> { if (Ux.run(this, () -> Shifts.close(s, Ui.num(x), "", n))) { load(); show(s); } }))), 8);
        }
        List<Shift> l = new ArrayList<>(); for (int i = Store.db.shifts.size() - 1; i >= 0 && l.size() < 200; i--) l.add(Store.db.shifts.get(i));
        ad.set(l);
    }
    void stats(LinearLayout c, Shift s) {
        c.addView(Ux.pair(this, "عدد الفواتير", String.valueOf(s.invoiceCount), Ux.TEXT));
        c.addView(Ux.pair(this, "المبيعات", Ui.m(s.sales), Ux.TEXT));
        c.addView(Ux.pair(this, "منها نقداً", Ui.m(s.cashSales), Ux.TEXT));
        c.addView(Ux.pair(this, "بطاقة / أخرى", Ui.m(s.cardSales), Ux.TEXT));
        c.addView(Ux.pair(this, "آجل", Ui.m(s.creditSales), Ux.TEXT));
        c.addView(Ux.pair(this, "المرتجعات", Ui.m(s.returns), Ux.TEXT));
        c.addView(Ux.pair(this, "المقبوضات", Ui.m(s.receipts), Ux.TEXT));
        c.addView(Ux.pair(this, "المدفوعات", Ui.m(s.payments), Ux.TEXT));
        c.addView(Ux.pair(this, "المصروفات", Ui.m(s.expenses), Ux.TEXT));
        c.addView(Ux.pair(this, "فكّة البداية", Ui.m(s.openingCash), Ux.TEXT));
        c.addView(Ux.pair(this, "النقد المتوقع في الدرج", Ui.m(s.expectedCash), Ux.PRIMARY));
        if (!s.isOpen()) { c.addView(Ux.pair(this, "المعدود فعلياً", Ui.m(s.countedCash), Ux.TEXT));
            c.addView(Ux.pair(this, s.diff < 0 ? "عجز" : s.diff > 0 ? "زيادة" : "الفرق", Ui.m(s.diff), Math.abs(s.diff) < 0.0005 ? Ux.GREEN : Ux.RED)); }
    }
    View row(Shift s, int pos) {
        return Ux.listRow(this, "وردية " + s.no + " — " + s.user, Ui.dt(s.opened) + (s.closed == null ? "  •  مفتوحة" : "  ←  " + Ui.dt(s.closed)),
                s.closed == null ? "مفتوحة" : (Math.abs(s.diff) < 0.0005 ? "مطابقة" : (s.diff < 0 ? "عجز " : "زيادة ") + Ui.m(Math.abs(s.diff))), s.closed == null || Math.abs(s.diff) < 0.0005 ? Ux.PRIMARY : Ux.RED);
    }
    void show(Shift s) {
        Shifts.compute(s);
        LinearLayout c = Ux.card(this); stats(c, s);
        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(c);
        String txt = "وردية " + s.no + " — " + s.user + "\n" + Ui.dt(s.opened) + " ← " + (s.closed == null ? "مفتوحة" : Ui.dt(s.closed))
                + "\nالمبيعات: " + Ui.m(s.sales) + "\nالنقد المتوقع: " + Ui.m(s.expectedCash) + (s.closed == null ? "" : "\nالمعدود: " + Ui.m(s.countedCash) + "\nالفرق: " + Ui.m(s.diff));
        new android.app.AlertDialog.Builder(this).setTitle("وردية رقم " + s.no).setView(sv).setPositiveButton("إغلاق", null)
                .setNeutralButton("مشاركة", (d, w) -> Share.text(this, "وردية " + s.no, txt)).show();
    }
}
