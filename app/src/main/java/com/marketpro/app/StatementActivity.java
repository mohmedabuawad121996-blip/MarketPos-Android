package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model;
import com.market.Model.Ledger;
import com.market.Model.Party;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** كشف حساب عميل أو مورد مع الرصيد المتراكم */
public class StatementActivity extends Activity {
    static final class Row { Ledger l; double run; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int kind = getIntent().getIntExtra("kind", Model.CUSTOMER); long id = getIntent().getLongExtra("id", 0);
        Party p = Store.db.party(id);
        LinearLayout body = Ux.screen(this, "كشف حساب: " + (p == null ? "" : p.name), false);
        List<Ledger> ls = new ArrayList<>();
        for (Ledger l : Store.db.ledger) if (l.partyId == id && l.partyKind == kind) ls.add(l);
        ls.sort(Comparator.comparing((Ledger l) -> l.date).thenComparingLong(l -> l.id));
        List<Row> rows = new ArrayList<>(); double run = 0, d = 0, c = 0;
        for (Ledger l : ls) { d += l.debit; c += l.credit; run = Ui.r(kind == Model.CUSTOMER ? d - c : c - d); Row r = new Row(); r.l = l; r.run = run; rows.add(r); }
        LinearLayout card = Ux.card(this);
        card.addView(Ux.pair(this, "مدين", Ui.m(d), Ux.TEXT)); card.addView(Ux.pair(this, "دائن", Ui.m(c), Ux.TEXT));
        card.addView(Ux.pair(this, "الرصيد", Ui.m(p == null ? run : Ops.balance(p)), Ux.RED));
        body.addView(card);
        Ux.add(body, Ux.button(this, "مشاركة الكشف (CSV)", Ux.BLUE, v -> {
            List<String[]> out = new ArrayList<>();
            for (Row r : rows) out.add(new String[]{Ui.dt(r.l.date), Ops.refTitle(r.l.ref) + " " + (r.l.refId == 0 ? "" : r.l.refId), r.l.notes, Ui.q(r.l.debit), Ui.q(r.l.credit), Ui.q(r.run)});
            Share.csv(this, "كشف_" + (p == null ? id : p.name), new String[]{"التاريخ", "البيان", "ملاحظات", "مدين", "دائن", "الرصيد"}, out);
        }), 8);
        java.util.Collections.reverse(rows);
        ListView list = new ListView(this); list.setDivider(null);
        Ux.Adapter<Row> ad = new Ux.Adapter<>(this::row); list.setAdapter(ad); ad.set(rows);
        Ux.add(body, list, 6); list.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    }
    View row(Row r, int pos) {
        String amt = r.l.debit != 0 ? "مدين " + Ui.m(r.l.debit) : "دائن " + Ui.m(r.l.credit);
        return Ux.listRow(this, Ops.refTitle(r.l.ref) + (r.l.notes == null || r.l.notes.isEmpty() ? "" : " — " + r.l.notes),
                Ui.dt(r.l.date) + "  •  " + amt, Ui.m(r.run), Ux.PRIMARY);
    }
}
