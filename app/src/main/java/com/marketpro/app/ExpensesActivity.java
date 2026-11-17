package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.market.Model.Expense;
import com.market.Model.ExpenseCat;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** المصاريف: تسجيل مصروف، القائمة، والإلغاء */
public class ExpensesActivity extends Activity {
    Ux.Adapter<Expense> ad; TextView sum;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "المصاريف", false);
        body.addView(Ux.button(this, "+ مصروف جديد", Ux.PRIMARY, v -> add()));
        sum = Ux.text(this, "", 13, true, Ux.PRIMARY); Ux.add(body, sum, 6);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> { Expense e = ad.getItem(i); if (e.cancelled) return;
            Ux.confirm(this, "إلغاء هذا المصروف وإرجاع مبلغه للصندوق؟", () -> { if (Ux.run(this, () -> Ops.cancel(e))) load(); }); });
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        load();
    }
    void load() {
        List<Expense> l = new ArrayList<>(); double month = 0; LocalDateTime m0 = Ui.start(LocalDate.now().withDayOfMonth(1));
        for (int i = Store.db.expenses.size() - 1; i >= 0; i--) { Expense e = Store.db.expenses.get(i);
            if (!e.cancelled && !e.date.isBefore(m0)) month += e.amount; if (l.size() < 300) l.add(e); }
        ad.set(l); sum.setText("مصاريف هذا الشهر: " + Ui.m(month));
    }
    View row(Expense e, int pos) {
        ExpenseCat c = Store.db.expenseCat(e.catId);
        return Ux.listRow(this, (c == null ? "مصروف" : c.name) + (e.cancelled ? " (ملغى)" : ""),
                Ui.dt(e.date) + (e.notes == null || e.notes.isEmpty() ? "" : "  •  " + e.notes), Ui.m(e.amount), e.cancelled ? Ux.MUTED : Ux.RED);
    }
    void add() {
        List<ExpenseCat> cs = Store.db.expenseCats; List<String> n = new ArrayList<>(); for (ExpenseCat c : cs) n.add(c.name); n.add("+ بند جديد");
        Ux.choose(this, "بند المصروف", n, i -> {
            if (i == cs.size()) { Ux.input(this, "اسم البند الجديد", "مثلاً: كهرباء", "", false, s -> { if (s.isEmpty()) return;
                ExpenseCat c = new ExpenseCat(); c.id = Store.db.next("expcat"); c.name = s; Store.db.expenseCats.add(c); Store.save(); amount(c); }); }
            else amount(cs.get(i));
        });
    }
    void amount(ExpenseCat c) {
        Ux.input(this, c.name + " — المبلغ", "المبلغ", "", true, s -> { double a = Ui.num(s);
            PartiesActivity.pickWallet(this, w -> Ux.input(this, "ملاحظات (اختياري)", "ملاحظات", "", false,
                    n -> { if (Ux.run(this, () -> Ops.expense(c.id, a, w, n, LocalDateTime.now()))) { Ux.toast(this, "تم تسجيل المصروف"); load(); } })); });
    }
}
