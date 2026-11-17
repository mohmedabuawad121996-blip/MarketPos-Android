package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model.CashMove;
import com.market.Model.Wallet;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;

/** الصندوق: أرصدة المحافظ، الإيداع والسحب اليدوي، التحويل، وآخر الحركات */
public class CashActivity extends Activity {
    LinearLayout card; Ux.Adapter<CashMove> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "الصندوق", false);
        card = Ux.card(this); body.addView(card);
        LinearLayout r = Ux.row(this);
        r.addView(Ux.button(this, "إيداع", Ux.PRIMARY, v -> manual(true)), Ux.weight(1));
        View g = new View(this); r.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        r.addView(Ux.button(this, "سحب", Ux.RED, v -> manual(false)), Ux.weight(1));
        View g2 = new View(this); r.addView(g2, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        r.addView(Ux.button(this, "تحويل", Ux.BLUE, v -> transfer()), Ux.weight(1));
        Ux.add(body, r, 8);
        Ux.add(body, Ux.text(this, "آخر الحركات", 15, true, Ux.TEXT), 10);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    @Override protected void onResume() { super.onResume(); load(); }

    void load() {
        card.removeAllViews(); double tot = 0;
        for (Wallet w : Store.db.activeWallets()) { double v = Ops.walletBalance(w.id); tot += v; card.addView(Ux.pair(this, w.name, Ui.m(v), v < 0 ? Ux.RED : Ux.TEXT)); }
        card.addView(Ux.pair(this, "الإجمالي", Ui.m(tot), Ux.PRIMARY));
        List<CashMove> l = new ArrayList<>(); List<CashMove> all = Store.db.cash;
        for (int i = all.size() - 1; i >= 0 && l.size() < 200; i--) l.add(all.get(i));
        ad.set(l);
    }
    View row(CashMove c, int pos) {
        return Ux.listRow(this, Ops.refTitle(c.ref) + (c.notes == null || c.notes.isEmpty() ? "" : " — " + c.notes),
                Ui.dt(c.date) + "  •  " + Store.db.walletName(c.walletId), Ui.m(c.amount), c.amount < 0 ? Ux.RED : Ux.PRIMARY);
    }
    void manual(boolean in) {
        Ux.input(this, in ? "إيداع في الصندوق" : "سحب من الصندوق", "المبلغ", "", true, s -> {
            double a = Ui.num(s);
            PartiesActivity.pickWallet(this, w -> Ux.input(this, "البيان (اختياري)", "ملاحظات", in ? "إيداع" : "سحب", false,
                    n -> { if (Ux.run(this, () -> Ops.cashManual(w, in ? a : -a, n))) load(); }));
        });
    }
    void transfer() {
        List<Wallet> ws = Store.db.activeWallets();
        if (ws.size() < 2) { Ux.alert(this, "يوجد صندوق واحد فقط. أضف محفظة أخرى من برنامج الكمبيوتر"); return; }
        List<String> n = new ArrayList<>(); for (Wallet w : ws) n.add(w.name);
        Ux.choose(this, "من", n, a -> Ux.choose(this, "إلى", n, b -> Ux.input(this, "المبلغ", "المبلغ", "", true,
                s -> { if (Ux.run(this, () -> Ops.transfer(ws.get(a).id, ws.get(b).id, Ui.num(s), "تحويل"))) load(); })));
    }
}
