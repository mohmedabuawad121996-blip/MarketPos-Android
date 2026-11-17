package com.marketpro.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.market.Accounting;
import com.market.Model;
import com.market.Model.Category;
import com.market.Model.Invoice;
import com.market.Model.Party;
import com.market.Model.Product;
import com.market.Model.Wallet;
import com.market.Model.Warehouse;
import com.market.ReportApi;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** تشغيل تقرير: خيارات التقرير (الفترة، الطرف، الصنف، …) ثم عرضه كجدول قابل للمشاركة */
public class ReportViewActivity extends Activity {
    ReportApi.Def def; final ReportApi.Params p = new ReportApi.Params();
    LinearLayout out; ReportApi.Table last; EditText noFrom, noTo, gold; CheckBox details;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int idx = getIntent().getIntExtra("index", 0);
        def = ReportApi.defs().get(idx);
        LinearLayout body = Ux.screen(this, def.name, false);
        ScrollView sv = new ScrollView(this); LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); sv.addView(col);
        body.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout opts = Ux.card(this); col.addView(opts);
        if (def.asOf) { p.from = LocalDate.of(2000, 1, 1); opts.addView(dateBtn("حتى تاريخ", false)); }
        else if (def.dates) { p.from = LocalDate.now().withDayOfMonth(1); opts.addView(rangeBtn()); }
        if (def.party >= 0) opts.addView(partyBtn());
        if (def.product) opts.addView(productBtn());
        if (def.wallet) opts.addView(walletBtn());
        if (def.payType) opts.addView(payBtn());
        if (def.category) opts.addView(categoryBtn());
        if (def.wh && ReportApi.multiWh()) opts.addView(whBtn());
        if (def.account) opts.addView(accountBtn());
        if (def.avail) opts.addView(availBtn());
        if (def.nos) { noFrom = Ux.field(this, "من فاتورة رقم", true); noTo = Ux.field(this, "إلى رقم (اختياري)", true); Ux.add(opts, noFrom, 6); Ux.add(opts, noTo, 6); }
        if (def.zakat) {
            gold = Ux.field(this, "سعر غرام الذهب عيار 24", true); gold.setText(Store.db.settings.getOrDefault("goldPrice", "")); Ux.add(opts, gold, 6);
            CheckBox sell = new CheckBox(this); sell.setText("تقييم البضاعة بسعر البيع"); sell.setChecked(true); sell.setOnCheckedChangeListener((x, c) -> p.atSelling = c); opts.addView(sell);
            CheckBox rec = new CheckBox(this); rec.setText("إدخال ديون العملاء"); rec.setChecked(true); rec.setOnCheckedChangeListener((x, c) -> p.includeRec = c); opts.addView(rec);
            CheckBox sol = new CheckBox(this); sol.setText("سنة ميلادية (2.577%)"); sol.setOnCheckedChangeListener((x, c) -> p.solar = c); opts.addView(sol);
        }
        if (def.details) { details = new CheckBox(this); details.setText("تفصيلي (أصناف الفواتير)"); opts.addView(details); }
        Ux.add(opts, Ux.button(this, "عرض التقرير", Ux.PRIMARY, v -> run()), 8);
        out = new LinearLayout(this); out.setOrientation(LinearLayout.VERTICAL); Ux.add(col, out, 8);
        if (!(def.party >= 0 && def.partyRequired) && !def.product && !def.nos) run();
    }

    Button opt(String text) { Button bt = Ux.button(this, text, Ux.TEAL, null); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = Ux.dp(this, 6); bt.setLayoutParams(lp); return bt; }

    View rangeBtn() {
        Button bt = opt(""); Runnable show = () -> bt.setText("الفترة: " + p.from + "  ←  " + p.to);
        show.run();
        bt.setOnClickListener(v -> {
            List<String> o = new ArrayList<>(); o.add("اليوم"); o.add("أمس"); o.add("آخر 7 أيام"); o.add("هذا الشهر"); o.add("الشهر الماضي"); o.add("هذه السنة"); o.add("كل الفترات"); o.add("تحديد يدوي");
            Ux.choose(this, "الفترة", o, i -> {
                LocalDate t = LocalDate.now();
                switch (i) {
                    case 0: p.from = p.to = t; break; case 1: p.from = p.to = t.minusDays(1); break; case 2: p.from = t.minusDays(6); p.to = t; break;
                    case 3: p.from = t.withDayOfMonth(1); p.to = t; break; case 4: p.from = t.minusMonths(1).withDayOfMonth(1); p.to = t.withDayOfMonth(1).minusDays(1); break;
                    case 5: p.from = t.withDayOfYear(1); p.to = t; break; case 6: p.from = LocalDate.of(2000, 1, 1); p.to = t; break;
                    default: Ux.input(this, "من تاريخ (yyyy-MM-dd)", "2026-01-01", p.from.toString(), false, a -> Ux.input(this, "إلى تاريخ (yyyy-MM-dd)", "2026-12-31", p.to.toString(), false, z -> {
                        try { LocalDate f = LocalDate.parse(a.trim()), e = LocalDate.parse(z.trim()); if (e.isBefore(f)) throw new Exception(); p.from = f; p.to = e; show.run(); run(); }
                        catch (Exception ex) { Ux.alert(this, "صيغة التاريخ غير صحيحة. مثال: 2026-11-17"); } })); return;
                }
                show.run(); run();
            });
        });
        return bt;
    }
    View dateBtn(String label, boolean from) {
        Button bt = opt(label + ": " + p.to);
        bt.setOnClickListener(v -> Ux.input(this, label + " (yyyy-MM-dd)", "2026-12-31", p.to.toString(), false, s -> {
            try { p.to = LocalDate.parse(s.trim()); bt.setText(label + ": " + p.to); run(); } catch (Exception e) { Ux.alert(this, "صيغة التاريخ غير صحيحة"); } }));
        return bt;
    }
    View partyBtn() {
        String noun = def.party == Model.CUSTOMER ? "العميل" : "المورد";
        Button bt = opt(noun + ": " + (def.partyRequired ? "اختر…" : "الكل"));
        bt.setOnClickListener(v -> {
            List<Party> ps = Store.db.parties(def.party); List<String> n = new ArrayList<>(); if (!def.partyRequired) n.add("— الكل —");
            for (Party x : ps) n.add(x.name);
            Ux.choose(this, noun, n, i -> { int k = def.partyRequired ? i : i - 1; p.party = k < 0 ? 0 : ps.get(k).id; bt.setText(noun + ": " + (k < 0 ? "الكل" : ps.get(k).name)); run(); });
        });
        return bt;
    }
    View productBtn() {
        Button bt = opt("الصنف: اختر…");
        bt.setOnClickListener(v -> Ux.input(this, "ابحث عن الصنف", "الاسم أو الباركود", "", false, q -> {
            List<Product> ps = new ArrayList<>(); List<String> n = new ArrayList<>(); String s = q.toLowerCase();
            for (Product x : Store.db.products.values()) if (x.name.toLowerCase().contains(s) || (x.barcode != null && x.barcode.contains(s))) { ps.add(x); n.add(x.name); if (ps.size() >= 60) break; }
            if (ps.isEmpty()) { Ux.alert(this, "لا يوجد صنف مطابق"); return; }
            Ux.choose(this, "الصنف", n, i -> { p.product = ps.get(i).id; bt.setText("الصنف: " + ps.get(i).name); run(); });
        }));
        return bt;
    }
    View walletBtn() {
        List<Wallet> ws = Store.db.wallets; if (!ws.isEmpty()) p.wallet = ws.get(0).id;
        Button bt = opt("المحفظة: " + (ws.isEmpty() ? "" : ws.get(0).name));
        bt.setOnClickListener(v -> { List<String> n = new ArrayList<>(); for (Wallet w : ws) n.add(w.name);
            Ux.choose(this, "المحفظة", n, i -> { p.wallet = ws.get(i).id; bt.setText("المحفظة: " + ws.get(i).name); run(); }); });
        return bt;
    }
    View payBtn() {
        Button bt = opt("طريقة الدفع: الكل");
        bt.setOnClickListener(v -> { List<Wallet> ws = Store.db.wallets; List<String> n = new ArrayList<>(); n.add("— كل طرق الدفع —"); n.add("آجل (غير مدفوع بالكامل)"); for (Wallet w : ws) n.add(w.name);
            Ux.choose(this, "طريقة الدفع", n, i -> { p.payType = i == 0 ? "" : i == 1 ? "credit" : "w:" + ws.get(i - 2).id; bt.setText("طريقة الدفع: " + n.get(i)); run(); }); });
        return bt;
    }
    View categoryBtn() {
        Button bt = opt("التصنيف: الكل");
        bt.setOnClickListener(v -> { List<Category> cs = Store.db.categories; List<String> n = new ArrayList<>(); n.add("— الكل —"); for (Category c : cs) n.add(c.name);
            Ux.choose(this, "التصنيف", n, i -> { p.category = i == 0 ? 0 : cs.get(i - 1).id; bt.setText("التصنيف: " + n.get(i)); run(); }); });
        return bt;
    }
    View whBtn() {
        Button bt = opt("المستودع: الكل");
        bt.setOnClickListener(v -> { List<Warehouse> ws = Store.db.activeWarehouses(); List<String> n = new ArrayList<>(); n.add("— كل المستودعات —"); for (Warehouse w : ws) n.add(w.name);
            Ux.choose(this, "المستودع", n, i -> { p.wh = i == 0 ? -1 : ws.get(i - 1).id; bt.setText("المستودع: " + n.get(i)); run(); }); });
        return bt;
    }
    View accountBtn() {
        List<Accounting.Acc> as = Accounting.chart(); if (!as.isEmpty()) p.account = as.get(0).key;
        Button bt = opt("الحساب: " + (as.isEmpty() ? "" : as.get(0).name));
        bt.setOnClickListener(v -> { List<String> n = new ArrayList<>(); for (Accounting.Acc a : as) n.add(a.code + "  " + a.name);
            Ux.choose(this, "الحساب", n, i -> { p.account = as.get(i).key; bt.setText("الحساب: " + as.get(i).name); run(); }); });
        return bt;
    }
    View availBtn() {
        Button bt = opt("الأصناف: الكل");
        bt.setOnClickListener(v -> { List<String> n = new ArrayList<>(); n.add("الكل"); n.add("المتوفرة فقط"); n.add("غير المتوفرة فقط");
            Ux.choose(this, "الأصناف", n, i -> { p.avail = i; bt.setText("الأصناف: " + n.get(i)); run(); }); });
        return bt;
    }

    void run() {
        if (noFrom != null) { p.noFrom = (long) Ui.num(noFrom.getText().toString()); p.noTo = (long) Ui.num(noTo.getText().toString()); }
        if (gold != null) { p.goldPrice = Ui.num(gold.getText().toString()); Store.db.set("goldPrice", gold.getText().toString().trim()); }
        if (details != null) p.details = details.isChecked();
        final ReportApi.Table[] t = new ReportApi.Table[1];
        if (!Ux.run(this, () -> t[0] = ReportApi.run(def.index, p))) return;
        last = t[0]; show(last);
    }

    void show(ReportApi.Table r) {
        out.removeAllViews();
        String store = Store.db.settings.getOrDefault("storeName", "");
        String period = def.asOf ? "حتى " + p.to : def.dates ? "من " + p.from + " إلى " + p.to : "حتى " + LocalDate.now();
        out.addView(Ux.text(this, (store.isEmpty() ? "" : store + "  •  ") + period, 13, false, Ux.MUTED));
        if (!r.summary.isEmpty()) Ux.add(out, Ux.text(this, r.summary.replace("  |  ", "\n"), 14, true, Ux.PRIMARY), 4);
        LinearLayout btns = Ux.row(this);
        btns.addView(Ux.button(this, "PDF", Ux.RED, v -> Pdf.shareTable(this, r.title, period, r)), Ux.weight(1));
        btns.addView(gap()); btns.addView(Ux.button(this, "Excel", Ux.BLUE, v -> Share.csv(this, r.title, r.cols, r.rows)), Ux.weight(1));
        btns.addView(gap()); btns.addView(Ux.button(this, "نص", Ux.TEAL, v -> Share.text(this, r.title, asText(r, period))), Ux.weight(1));
        Ux.add(out, btns, 8);
        if (r.rows.isEmpty()) { Ux.add(out, Ux.text(this, "لا توجد بيانات", 15, true, Ux.MUTED), 20); return; }
        TableLayout t = new TableLayout(this); t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.addView(tr(r.cols, Ux.HEAD, Ux.TEXT, true, null));
        for (int i = 0; i < r.rows.size(); i++) {
            String[] row = r.rows.get(i); boolean total = row.length > 1 && (String.join("", row).contains("الإجمالي") || (row[0] != null && row[0].startsWith("المجموع")));
            Long invId = r.inv.get(i);
            t.addView(tr(row, total ? Ux.SELECT : i % 2 == 0 ? Ux.ROW : Ux.ROW_ALT, total ? Ux.TEXT : invId != null ? Ux.PRIMARY : Ux.TEXT, total, invId));
        }
        HorizontalScrollView h = new HorizontalScrollView(this); h.addView(t);
        Ux.add(out, h, 8);
        if (r.inv.stream().anyMatch(x -> x != null)) Ux.add(out, Ux.text(this, "اضغط على أي فاتورة (باللون الأزرق) لعرضها", 12, false, Ux.MUTED), 4);
    }
    View gap() { View g = new View(this); g.setLayoutParams(new LinearLayout.LayoutParams(Ux.dp(this, 6), 1)); return g; }
    TableRow tr(String[] cells, int bg, int fg, boolean bold, Long invId) {
        TableRow row = new TableRow(this); row.setBackgroundColor(bg);
        for (int c = 0; c < cells.length; c++) {
            TextView tv = new TextView(this); tv.setText(cells[c] == null ? "" : cells[c]); tv.setTextColor(fg); tv.setTextSize(14);
            if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
            int pd = Ux.dp(this, 8); tv.setPadding(pd, Ux.dp(this, 7), pd, Ux.dp(this, 7));
            tv.setGravity(Gravity.CENTER); tv.setMaxWidth(Ux.dp(this, 220));
            row.addView(tv);
        }
        if (invId != null) row.setOnClickListener(v -> { Invoice i = Store.db.invoice(invId); if (i != null) InvoicesActivity.details(this, i, null); });
        return row;
    }
    static String asText(ReportApi.Table r, String period) {
        StringBuilder s = new StringBuilder(r.title).append('\n').append(period).append('\n');
        if (!r.summary.isEmpty()) s.append(r.summary).append('\n');
        s.append('\n').append(String.join(" | ", r.cols)).append('\n');
        for (String[] row : r.rows) s.append(String.join(" | ", row)).append('\n');
        return s.toString();
    }
}
