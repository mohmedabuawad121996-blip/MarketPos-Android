package com.marketpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Accounting;
import com.market.Model.JournalEntry;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** المحاسبة: القيود اليدوية، دليل الحسابات، والتقارير المحاسبية */
public class JournalActivity extends Activity {
    Ux.Adapter<JournalEntry> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "المحاسبة — القيود اليدوية", false);
        LinearLayout r = Ux.row(this);
        r.addView(Ux.button(this, "+ قيد جديد", Ux.GREEN, v -> newEntry(new ArrayList<>(), "")), Ux.weight(1));
        View g = new View(this); r.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        r.addView(Ux.button(this, "دليل الحسابات", Ux.BLUE, v -> chart()), Ux.weight(1));
        body.addView(r);
        Ux.add(body, Ux.text(this, "التقارير المحاسبية (اليومية، الأستاذ، ميزان المراجعة، قائمة الدخل، الميزانية) في التقارير ← المحاسبة", 12, false, Ux.MUTED), 6);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> show(ad.getItem(i)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 8); body.addView(list, lp);
        load();
        String d = getIntent().getStringExtra("do");
        if ("chart".equals(d)) chart(); else if ("new".equals(d)) newEntry(new ArrayList<>(), "");
    }
    void load() { List<JournalEntry> l = new ArrayList<>(); for (int i = Store.db.journal.size() - 1; i >= 0; i--) l.add(Store.db.journal.get(i)); ad.set(l); }
    View row(JournalEntry j, int pos) {
        Accounting.Entry e = Accounting.manual(j);
        return Ux.listRow(this, "قيد " + j.no + (j.notes.isEmpty() ? "" : " — " + j.notes) + (j.cancelled ? " (ملغى)" : ""), Ui.dt(j.date) + "  •  " + e.lines.size() + " أسطر  •  " + j.user,
                Ui.m(e.debit()), j.cancelled ? Ux.MUTED : Ux.PRIMARY);
    }
    void show(JournalEntry j) {
        Map<String, Accounting.Acc> m = Accounting.chartMap(); StringBuilder s = new StringBuilder();
        for (String[] a : Accounting.parse(j.lines)) s.append(Accounting.accName(m, a[0])).append("\n    مدين ").append(Ui.m(Ui.num(a[1]))).append("   دائن ").append(Ui.m(Ui.num(a[2])))
                .append(a[3].isEmpty() ? "" : "   (" + a[3] + ")").append('\n');
        Dlg bd = new Dlg(this).setTitle("قيد رقم " + j.no + " — " + Ui.dt(j.date)).setMessage(s.toString()).setPositiveButton("إغلاق", null);
        if (!j.cancelled) bd.setNeutralButton("إلغاء القيد", (d, w) -> Ux.confirm(this, "إلغاء القيد رقم " + j.no + "؟ سيُسجَّل قيد عكسي له.", () -> { j.cancelled = true; Store.save(); load(); }));
        bd.show();
    }
    /** أسطر القيد: {الحساب، مدين، دائن، بيان} */
    void newEntry(List<String[]> lines, String notes) {
        Map<String, Accounting.Acc> m = Accounting.chartMap(); double d = 0, c = 0; StringBuilder s = new StringBuilder();
        for (String[] a : lines) { d += Ui.num(a[1]); c += Ui.num(a[2]); s.append("• ").append(Accounting.accName(m, a[0])).append(Ui.num(a[1]) > 0 ? "  مدين " + Ui.m(Ui.num(a[1])) : "  دائن " + Ui.m(Ui.num(a[2]))).append('\n'); }
        s.append("\nالمدين: ").append(Ui.m(d)).append("   الدائن: ").append(Ui.m(c)).append(Math.abs(d - c) > 0.0005 ? "\nالفرق: " + Ui.m(Math.abs(d - c)) : "\n✓ متوازن");
        new Dlg(this).setTitle("قيد يومية جديد").setMessage(s.toString())
                .setPositiveButton("+ سطر", (x, w) -> addLine(lines, notes))
                .setNeutralButton("حفظ القيد", (x, w) -> Ux.input(this, "بيان القيد", "البيان", notes, false, n -> {
                    if (!Ux.run(this, () -> Accounting.saveManual(LocalDateTime.now(), n, lines))) newEntry(lines, n); else load(); }))
                .setNegativeButton("إلغاء", null).show();
    }
    void addLine(List<String[]> lines, String notes) {
        List<Accounting.Acc> as = new ArrayList<>(); List<String> n = new ArrayList<>();
        for (Accounting.Acc a : Accounting.chart()) if (Accounting.manualAllowed(a.key)) { as.add(a); n.add(a.code + "  " + a.name); }
        Ux.choose(this, "الحساب", n, i -> Ux.choose(this, as.get(i).name, java.util.Arrays.asList("مدين", "دائن"), side -> Ux.input(this, "المبلغ", "المبلغ", "", true, amt -> {
            double v = Ui.num(amt); if (v > 0) lines.add(new String[]{as.get(i).key, side == 0 ? String.valueOf(v) : "0", side == 1 ? String.valueOf(v) : "0", ""});
            newEntry(lines, notes); })));
    }
    void chart() {
        Map<String, double[]> tot = Accounting.totals(Accounting.all(), null, null);
        StringBuilder s = new StringBuilder(); int type = -1;
        for (Accounting.Acc a : Accounting.chart()) {
            if (a.type != type) { type = a.type; s.append("\n【").append(Accounting.TYPES[type]).append("】\n"); }
            s.append(a.code).append("  ").append(a.name).append(":  ").append(Ui.m(Accounting.bal(a, tot.get(a.key)))).append('\n');
        }
        new Dlg(this).setTitle("دليل الحسابات والأرصدة").setMessage(s.toString().trim()).setPositiveButton("إغلاق", null)
                .setNeutralButton("مشاركة", (d, w) -> Share.text(this, "دليل الحسابات", s.toString())).show();
    }
}
