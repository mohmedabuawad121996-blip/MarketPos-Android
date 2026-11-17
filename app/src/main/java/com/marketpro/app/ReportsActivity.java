package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import com.market.Model;
import com.market.Model.Expense;
import com.market.Model.ExpenseCat;
import com.market.Model.InvLine;
import com.market.Model.InvType;
import com.market.Model.Invoice;
import com.market.Model.Party;
import com.market.Model.Product;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** التقارير: يحسب التقرير ثم يعرضه في شاشة جدول قابلة للمشاركة */
public class ReportsActivity extends Activity {
    /** تقرير جاهز للعرض */
    public static final class Report {
        public String title, sub = ""; public String[] head; public List<String[]> rows = new ArrayList<>(); public String[] total;
        public List<String[]> summary = new ArrayList<>(); // أزواج (عنوان، قيمة) أعلى التقرير
    }
    static Report current;

    LocalDate from = LocalDate.now(), to = LocalDate.now(); Button rangeB;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "التقارير", true);
        rangeB = Ux.button(this, "", Ux.TEAL, v -> range()); body.addView(rangeB); showRange();
        boolean cost = Session.cap("cap:cost");
        item(body, "ملخص المبيعات والأرباح", Ux.PRIMARY, this::salesSummary);
        item(body, "المبيعات اليومية", Ux.PRIMARY, this::daily);
        if (cost) item(body, "أرباح الأصناف", Ux.GREEN, this::productProfit);
        item(body, "الأصناف الأكثر مبيعاً", Ux.GREEN, this::topSold);
        item(body, "تفصيل الخصومات (بيع وشراء)", Ux.ORANGE, this::discounts);
        item(body, "المشتريات", Ux.BLUE, () -> invoices(InvType.PURCHASE));
        item(body, "المصاريف حسب البند", Ux.RED, this::expenses);
        item(body, "جرد المخزون مع المجاميع", Ux.PURPLE, this::stock);
        item(body, "الأصناف تحت الحد الأدنى", Ux.PURPLE, this::low);
        item(body, "أرصدة العملاء", Ux.DARK, () -> balances(Model.CUSTOMER));
        item(body, "أرصدة الموردين", Ux.DARK, () -> balances(Model.SUPPLIER));
    }
    void item(LinearLayout body, String t, int c, Runnable r) {
        Ux.add(body, Ux.button(this, t, c, v -> { if (Ux.run(this, r)) { if (current != null) startActivity(new Intent(this, ReportViewActivity.class)); } }), 8);
    }
    void showRange() { rangeB.setText("الفترة: " + (from.equals(to) ? from.toString() : from + "  ←  " + to) + "  (تغيير)"); }
    void range() {
        List<String> o = new ArrayList<>(); o.add("اليوم"); o.add("أمس"); o.add("آخر 7 أيام"); o.add("هذا الشهر"); o.add("الشهر الماضي"); o.add("هذه السنة"); o.add("تحديد يدوي");
        Ux.choose(this, "الفترة", o, i -> {
            LocalDate t = LocalDate.now();
            switch (i) {
                case 0: from = to = t; break; case 1: from = to = t.minusDays(1); break; case 2: from = t.minusDays(6); to = t; break;
                case 3: from = t.withDayOfMonth(1); to = t; break; case 4: from = t.minusMonths(1).withDayOfMonth(1); to = t.withDayOfMonth(1).minusDays(1); break;
                case 5: from = t.withDayOfYear(1); to = t; break;
                default: Ux.input(this, "من تاريخ (yyyy-MM-dd)", "2026-01-01", from.toString(), false, a -> Ux.input(this, "إلى تاريخ (yyyy-MM-dd)", "2026-12-31", to.toString(), false, z -> {
                    try { LocalDate f = LocalDate.parse(a.trim()), e = LocalDate.parse(z.trim()); if (e.isBefore(f)) throw new Exception(); from = f; to = e; showRange(); }
                    catch (Exception ex) { Ux.alert(this, "صيغة التاريخ غير صحيحة. مثال: 2026-11-17"); } })); return;
            }
            showRange();
        });
    }
    boolean in(LocalDateTime d) { return !d.isBefore(Ui.start(from)) && d.isBefore(Ui.end(to)); }
    String period() { return "الفترة: " + from + " إلى " + to; }
    Report rep(String t, String... head) { Report r = new Report(); r.title = t; r.sub = period(); r.head = head; current = r; return r; }
    List<Invoice> inv(InvType t) { List<Invoice> l = new ArrayList<>(); for (Invoice i : Store.db.invoices) if (i.type == t && !i.cancelled && in(i.date)) l.add(i); return l; }

    void salesSummary() {
        Report r = rep("ملخص المبيعات والأرباح", "البند", "القيمة");
        double sales = 0, disc = 0, tax = 0, paid = 0, cost = 0, ret = 0, retCost = 0, purch = 0, exp = 0; int n = 0;
        for (Invoice i : inv(InvType.SALE)) { n++; sales += i.total; disc += i.discount; for (InvLine l : i.lines) disc += l.discount; tax += i.tax; paid += Math.min(i.paid, i.total); cost += i.costTotal(); }
        for (Invoice i : inv(InvType.SALE_RETURN)) { ret += i.total; retCost += i.costTotal(); tax -= i.tax; }
        for (Invoice i : inv(InvType.PURCHASE)) purch += i.total;
        for (Expense e : Store.db.expenses) if (!e.cancelled && in(e.date)) exp += e.amount;
        double net = sales - ret, gross = (sales - ret - tax) - (cost - retCost);
        r.rows.add(new String[]{"عدد فواتير البيع", String.valueOf(n)});
        r.rows.add(new String[]{"إجمالي المبيعات", Ui.m(sales)});
        r.rows.add(new String[]{"مرتجع المبيعات", Ui.m(ret)});
        r.rows.add(new String[]{"صافي المبيعات", Ui.m(net)});
        r.rows.add(new String[]{"الخصومات الممنوحة", Ui.m(disc)});
        if (tax != 0) r.rows.add(new String[]{"الضريبة", Ui.m(tax)});
        r.rows.add(new String[]{"المقبوض نقداً من الفواتير", Ui.m(paid)});
        r.rows.add(new String[]{"المبيعات الآجلة", Ui.m(sales - paid)});
        if (Session.cap("cap:cost")) {
            r.rows.add(new String[]{"تكلفة البضاعة المباعة", Ui.m(cost - retCost)});
            r.rows.add(new String[]{"مجمل الربح", Ui.m(gross)});
            r.rows.add(new String[]{"المصاريف", Ui.m(exp)});
            r.rows.add(new String[]{"صافي الربح", Ui.m(gross - exp)});
        }
        r.rows.add(new String[]{"المشتريات", Ui.m(purch)});
        if (n > 0) r.rows.add(new String[]{"متوسط الفاتورة", Ui.m(sales / n)});
    }
    void daily() {
        Report r = rep("المبيعات اليومية", "اليوم", "عدد", "المبيعات", "المرتجع", "الصافي");
        TreeMap<LocalDate, double[]> m = new TreeMap<>(Comparator.reverseOrder());
        for (Invoice i : inv(InvType.SALE)) { double[] a = m.computeIfAbsent(i.date.toLocalDate(), k -> new double[3]); a[0]++; a[1] += i.total; }
        for (Invoice i : inv(InvType.SALE_RETURN)) m.computeIfAbsent(i.date.toLocalDate(), k -> new double[3])[2] += i.total;
        double c = 0, s = 0, t = 0;
        for (Map.Entry<LocalDate, double[]> e : m.entrySet()) { double[] a = e.getValue(); c += a[0]; s += a[1]; t += a[2];
            r.rows.add(new String[]{e.getKey().toString(), Ui.q(a[0]), Ui.m(a[1]), Ui.m(a[2]), Ui.m(a[1] - a[2])}); }
        r.total = new String[]{"المجموع", Ui.q(c), Ui.m(s), Ui.m(t), Ui.m(s - t)};
    }
    /** كمية/مبيعات/تكلفة لكل صنف (المرتجع يُطرح) */
    Map<Long, double[]> byProduct() {
        Map<Long, double[]> m = new LinkedHashMap<>();
        for (InvType t : new InvType[]{InvType.SALE, InvType.SALE_RETURN}) { int s = t == InvType.SALE ? 1 : -1;
            for (Invoice i : inv(t)) { double share = i.subtotal == 0 ? 0 : i.discount / i.subtotal;
                for (InvLine l : i.lines) { double[] a = m.computeIfAbsent(l.productId, k -> new double[3]);
                    a[0] += s * l.pieces(); a[1] += s * l.total() * (1 - share); a[2] += s * l.cost * l.pieces(); } } }
        return m;
    }
    String pname(long id) { Product p = Store.db.products.get(id); return p == null ? "#" + id : p.name; }
    void productProfit() {
        Report r = rep("أرباح الأصناف", "الصنف", "الكمية", "المبيعات", "التكلفة", "الربح", "%");
        List<Map.Entry<Long, double[]>> l = new ArrayList<>(byProduct().entrySet());
        l.sort((a, b) -> Double.compare(b.getValue()[1] - b.getValue()[2], a.getValue()[1] - a.getValue()[2]));
        double s = 0, c = 0;
        for (Map.Entry<Long, double[]> e : l) { double[] a = e.getValue(); s += a[1]; c += a[2];
            r.rows.add(new String[]{pname(e.getKey()), Ui.q(a[0]), Ui.m(a[1]), Ui.m(a[2]), Ui.m(a[1] - a[2]), a[1] == 0 ? "" : Ui.q(Ui.r((a[1] - a[2]) * 100 / a[1], 1))}); }
        r.total = new String[]{"المجموع", "", Ui.m(s), Ui.m(c), Ui.m(s - c), s == 0 ? "" : Ui.q(Ui.r((s - c) * 100 / s, 1))};
    }
    void topSold() {
        Report r = rep("الأصناف الأكثر مبيعاً", "الصنف", "الكمية", "المبيعات");
        List<Map.Entry<Long, double[]>> l = new ArrayList<>(byProduct().entrySet());
        l.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
        double s = 0; for (Map.Entry<Long, double[]> e : l) { s += e.getValue()[1]; r.rows.add(new String[]{pname(e.getKey()), Ui.q(e.getValue()[0]), Ui.m(e.getValue()[1])}); }
        r.total = new String[]{"المجموع", "", Ui.m(s)};
    }
    void discounts() {
        Report r = rep("تفصيل الخصومات", "التاريخ", "المستند", "الطرف", "خصم الأصناف", "خصم الفاتورة", "المجموع");
        double ls = 0, is = 0;
        for (InvType t : new InvType[]{InvType.SALE, InvType.PURCHASE, InvType.SALE_RETURN, InvType.PURCHASE_RETURN})
            for (Invoice i : inv(t)) { double ld = 0; for (InvLine l : i.lines) ld += l.discount; if (ld == 0 && i.discount == 0) continue;
                int sg = t == InvType.SALE || t == InvType.PURCHASE ? 1 : -1; ls += sg * ld; is += sg * i.discount;
                r.rows.add(new String[]{Ui.dt(i.date), t.title + " " + i.no, i.partyName == null || i.partyName.isEmpty() ? Store.db.partyName(i.partyId) : i.partyName, Ui.m(ld), Ui.m(i.discount), Ui.m(ld + i.discount)}); }
        r.total = new String[]{"الصافي", "", "", Ui.m(ls), Ui.m(is), Ui.m(ls + is)};
    }
    void invoices(InvType t) {
        Report r = rep(t.title, "التاريخ", "الرقم", "الطرف", "المجموع", "المدفوع");
        double s = 0, p = 0;
        for (Invoice i : inv(t)) { s += i.total; p += i.paid; r.rows.add(new String[]{Ui.dt(i.date), String.valueOf(i.no), Store.db.partyName(i.partyId), Ui.m(i.total), Ui.m(i.paid)}); }
        r.total = new String[]{"المجموع", String.valueOf(r.rows.size()), "", Ui.m(s), Ui.m(p)};
    }
    void expenses() {
        Report r = rep("المصاريف حسب البند", "البند", "العدد", "المبلغ");
        Map<Long, double[]> m = new LinkedHashMap<>(); double s = 0;
        for (Expense e : Store.db.expenses) if (!e.cancelled && in(e.date)) { double[] a = m.computeIfAbsent(e.catId, k -> new double[2]); a[0]++; a[1] += e.amount; s += e.amount; }
        for (Map.Entry<Long, double[]> e : m.entrySet()) { ExpenseCat c = Store.db.expenseCat(e.getKey()); r.rows.add(new String[]{c == null ? "-" : c.name, Ui.q(e.getValue()[0]), Ui.m(e.getValue()[1])}); }
        r.total = new String[]{"المجموع", "", Ui.m(s)};
    }
    void stock() {
        boolean cost = Session.cap("cap:cost");
        Report r = cost ? rep("جرد المخزون", "الصنف", "الكمية", "التكلفة", "قيمة التكلفة", "قيمة البيع", "الربح المتوقع") : rep("جرد المخزون", "الصنف", "الكمية", "سعر البيع", "قيمة البيع");
        r.sub = "حتى تاريخ " + LocalDate.now();
        double c = 0, s = 0; int n = 0;
        List<Product> ps = new ArrayList<>(Store.db.products.values()); ps.sort(Comparator.comparing(p -> p.name));
        for (Product p : ps) { if (!p.active) continue; n++; double cv = p.qty * p.cost, sv = p.qty * p.price; c += cv; s += sv;
            r.rows.add(cost ? new String[]{p.name, p.stockText(), Ui.m(p.cost), Ui.m(cv), Ui.m(sv), Ui.m(sv - cv)} : new String[]{p.name, p.stockText(), Ui.m(p.price), Ui.m(sv)}); }
        r.total = cost ? new String[]{"المجموع (" + n + " صنف)", "", "", Ui.m(c), Ui.m(s), Ui.m(s - c)} : new String[]{"المجموع (" + n + " صنف)", "", "", Ui.m(s)};
    }
    void low() {
        Report r = rep("الأصناف تحت الحد الأدنى", "الصنف", "الكمية", "الحد الأدنى", "النقص");
        r.sub = "حتى تاريخ " + LocalDate.now();
        for (Product p : Store.db.products.values()) if (p.active && p.minQty > 0 && p.qty < p.minQty)
            r.rows.add(new String[]{p.name, p.stockText(), Ui.q(p.minQty), Ui.q(p.minQty - p.qty)});
    }
    void balances(int kind) {
        Report r = rep(kind == Model.CUSTOMER ? "أرصدة العملاء" : "أرصدة الموردين", "الاسم", "الهاتف", "الرصيد");
        r.sub = "حتى تاريخ " + LocalDate.now();
        Map<Long, Double> b = Ops.allBalances(kind); double s = 0;
        for (Party p : Store.db.parties(kind)) { double v = b.getOrDefault(p.id, 0.0); if (Math.abs(v) < 0.0005) continue; s += v; r.rows.add(new String[]{p.name, p.phone, Ui.m(v)}); }
        r.total = new String[]{"المجموع", "", Ui.m(s)};
    }
}
