package com.market;

import com.market.Model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import static com.market.ReportsStock.*;
import static com.market.ReportsMoney.*;
import static com.market.ReportsParties.*;

/** مركز التقارير — منطق الحساب فقط (نسخة الهاتف) */
public class ReportsUI {

    static class Result { String[] cols; List<Object[]> rows = new ArrayList<>(); List<Long> inv = new ArrayList<>(); String summary = "";
        Result(String... c) { cols = c; }
        void add(Object... r) { rows.add(r); inv.add(null); }
        /** صف مرتبط بفاتورة (يمكن معاينتها أو تعديلها بالنقر المزدوج) */
        void addInv(long invoiceId, Object... r) { rows.add(r); inv.add(invoiceId); }
        /** سطر حركة: يُربط بالفاتورة إن كان مصدره فاتورة موجودة */
        void addRef(String ref, long refId, Object... r) { Invoice i = invRef(ref, refId); rows.add(r); inv.add(i == null ? null : i.id); }
        boolean hasInvoices() { for (Long l : inv) if (l != null) return true; return false; } }

    static Invoice invRef(String ref, long refId) {
        if (refId == 0 || ref == null) return null;
        for (InvType t : InvType.values()) if (ref.equals(t.name())) { Invoice i = db().invoice(refId); return i != null && i.type == t ? i : null; }
        return null;
    }
    static class P { LocalDate from, to; long party; Product product; long wallet; boolean details;
        long category; String payType = ""; long noFrom, noTo; int avail; long wh = -1; String account = ""; boolean atSelling, includeRec, solar; double goldPrice; }
    static class Rep { String group, name; int party = -1; boolean partyRequired, product, wallet, dates = true, details, category, payType, nos, avail, zakat, wh, account, asOf; Function<P, Result> fn;
        Rep(String g, String n, Function<P, Result> f) { group = g; name = n; fn = f; }
        Rep party(int k, boolean req) { party = k; partyRequired = req; return this; } Rep product() { product = true; return this; }
        Rep wallet() { wallet = true; return this; } Rep noDates() { dates = false; return this; } Rep details() { details = true; return this; }
        Rep category() { category = true; return this; } Rep payType() { payType = true; return this; } Rep nos() { nos = true; dates = false; return this; }
        Rep wh() { wh = true; return this; } Rep account() { account = true; return this; } Rep asOf() { asOf = true; return this; } Rep avail() { avail = true; return this; } Rep zakat() { zakat = true; return this; } }

    static final List<Rep> REPS = new ArrayList<>();
    static Store db() { return Store.db; }

    static {
        String S = "تقارير المبيعات", PR = "تقارير الأرباح والأداء", G1 = "تقارير المخزون", G2 = "الصندوق والحسابات", CZ = "رأس المال والزكاة",
                G3 = "المصروفات", G4 = "العملاء والذمم", G5 = "الموردون والمشتريات", TX = "الضرائب", AC = "المحاسبة";

        // ===== تقارير المبيعات =====
        REPS.add(new Rep(S, "المبيعات - الكل", p -> sales(p, "all")).party(Model.CUSTOMER, false).payType());
        REPS.add(new Rep(S, "المبيعات الآجلة", p -> sales(p, "credit")).party(Model.CUSTOMER, false));
        REPS.add(new Rep(S, "المبيعات حسب طريقة الدفع (نقدي/بطاقة/شيك)", p -> sales(p, "pay")).payType().party(Model.CUSTOMER, false));
        REPS.add(new Rep(S, "المبيعات حسب رقم الفاتورة", p -> sales(p, "nos")).nos());
        REPS.add(new Rep(S, "المبيعات حسب العميل", ReportsUI::salesByCustomer));
        REPS.add(new Rep(S, "المبيعات حسب التصنيف", p -> byCategory(p, false)).category());
        REPS.add(new Rep(S, "المبيعات حسب الصنف", p -> byItem(p, false)).category());
        REPS.add(new Rep(S, "المبيعات الشهرية", ReportsStock::salesMonthly));
        REPS.add(new Rep(S, "المبيعات حسب الكاشير", ReportsStock::salesByCashier));
        REPS.add(new Rep(S, "أوقات الذروة (حسب الساعة واليوم)", ReportsMoney::peakHours));
        REPS.add(new Rep(S, "الخصومات الممنوحة", ReportsStock::discountsGiven).party(Model.CUSTOMER, false));
        REPS.add(new Rep(S, "الخصومات على المبيعات (تفصيلي)", p -> ReportsStock.discountsDetail(p, InvType.SALE)).party(Model.CUSTOMER, false).category());
        REPS.add(new Rep(S, "مبيعات العروض", ReportsUI::offerSales));
        REPS.add(new Rep(S, "عروض الأسعار", p -> invoices(p, InvType.QUOTE)).party(Model.CUSTOMER, false));
        REPS.add(new Rep(S, "مرتجعات المبيعات", p -> invoices(p, InvType.SALE_RETURN)).party(Model.CUSTOMER, false).payType());
        REPS.add(new Rep(S, "مرتجعات المبيعات حسب الصنف", ReportsStock::returnsByItem).category());
        REPS.add(new Rep(S, "الفواتير الملغاة", ReportsStock::cancelledInvoices));

        // ===== تقارير الأرباح والأداء =====
        REPS.add(new Rep(PR, "المبيعات والأرباح - إجمالي", ReportsUI::salesProfitDaily));
        REPS.add(new Rep(PR, "الأرباح والخسائر", ReportsMoney::profit));
        REPS.add(new Rep(PR, "مقارنة فترتين (المبيعات والأرباح)", ReportsMoney::comparePeriods));
        REPS.add(new Rep(PR, "الأرباح حسب الصنف", p -> byItem(p, true)).category());
        REPS.add(new Rep(PR, "الأرباح حسب التصنيف", p -> byCategory(p, true)).category());
        REPS.add(new Rep(PR, "الأرباح لتصنيف", ReportsUI::profitOfCategory).category());
        REPS.add(new Rep(PR, "الأرباح حسب العميل", ReportsUI::profitByCustomer));
        REPS.add(new Rep(PR, "الأرباح حسب الفاتورة", ReportsUI::profitByInvoice).party(Model.CUSTOMER, false));
        REPS.add(new Rep(PR, "الأرباح حسب رقم الفاتورة", ReportsUI::profitByInvoiceNo).nos());
        REPS.add(new Rep(PR, "الأرباح حسب الفرع", ReportsUI::profitByBranch));
        REPS.add(new Rep(PR, "أصناف بربح منخفض أو بخسارة", ReportsStock::lowMargin).category());
        REPS.add(new Rep(PR, "أصناف تحتاج إعادة تسعير", ReportsStock::needsRepricing));

        // ===== تقارير المخزون =====
        REPS.add(new Rep(G1, "جرد المخزون", ReportsStock::inventory).noDates().avail().category().wh());
        REPS.add(new Rep(G1, "جرد المخزون - المنتجات المتوفرة", p -> { p.avail = 1; return inventory(p); }).noDates().category().wh());
        REPS.add(new Rep(G1, "جرد المخزون حسب التصنيف", ReportsStock::inventoryByCategory).noDates().avail().wh());
        REPS.add(new Rep(G1, "جرد مخزون لتصنيف", ReportsStock::inventoryOfCategory).noDates().avail().category().wh());
        REPS.add(new Rep(G1, "أرصدة المستودعات", ReportsStock::warehouses).noDates().category());
        REPS.add(new Rep(G1, "تحويلات المستودعات", ReportsStock::transfers));
        REPS.add(new Rep(G1, "حركة المخزون للفترة", ReportsStock::stockMovement).wh());
        REPS.add(new Rep(G1, "حركة صنف", ReportsStock::itemMovement).product().wh());
        REPS.add(new Rep(G1, "الأصناف الأكثر مبيعاً", ReportsStock::topSelling).category());
        REPS.add(new Rep(G1, "الأصناف الراكدة (بدون حركة بيع)", ReportsStock::stagnant).category());
        REPS.add(new Rep(G1, "أصناف تحت حد الطلب", ReportsStock::lowStock).noDates());
        REPS.add(new Rep(G1, "المنتجات حسب تاريخ الانتهاء", ReportsStock::expiryReport).noDates().category());
        REPS.add(new Rep(G1, "المنتجات التالفة", ReportsStock::damages));
        REPS.add(new Rep(G1, "أصناف ناقصة البيانات", ReportsStock::missingData).noDates());

        // ===== العملاء والذمم =====
        REPS.add(new Rep(G4, "أرصدة العملاء", p -> balances(Model.CUSTOMER)).noDates());
        REPS.add(new Rep(G4, "أكبر العملاء المدينين", ReportsParties::topDebtors).noDates());
        REPS.add(new Rep(G4, "أعمار ذمم العملاء", ReportsStock::agingCustomers).noDates());
        REPS.add(new Rep(G4, "كشف حساب عميل", p -> statement(p, Model.CUSTOMER)).party(Model.CUSTOMER, true).details());
        REPS.add(new Rep(G4, "مصادقة رصيد عميل", p -> confirmation(p, Model.CUSTOMER)).party(Model.CUSTOMER, true).asOf());
        REPS.add(new Rep(G4, "فواتير عميل", p -> invoices(p, InvType.SALE)).party(Model.CUSTOMER, false));
        REPS.add(new Rep(G4, "فواتير العملاء - إجمالي", p -> invoicesByParty(p, Model.CUSTOMER)));
        REPS.add(new Rep(G4, "إجمالي حسب الصنف لعميل", ReportsParties::itemsForCustomer).party(Model.CUSTOMER, true).category());
        REPS.add(new Rep(G4, "سندات القبض", p -> vouchers(p, Model.CUSTOMER)).party(Model.CUSTOMER, false));
        REPS.add(new Rep(G4, "سندات الصرف لعميل", p -> vouchersDir(p, Model.CUSTOMER, false)).party(Model.CUSTOMER, false));
        REPS.add(new Rep(G4, "حركة السداد للعملاء", p -> payments(p, Model.CUSTOMER)).party(Model.CUSTOMER, false));
        REPS.add(new Rep(G4, "حركة السداد للعملاء حسب طريقة الدفع", p -> paymentsByMethod(p, Model.CUSTOMER)).party(Model.CUSTOMER, false));

        // ===== الموردون والمشتريات =====
        REPS.add(new Rep(G5, "مشتريات حسب المورد", ReportsParties::purchasesBySupplier));
        REPS.add(new Rep(G5, "المشتريات حسب الصنف", ReportsStock::purchasesByItem).category());
        REPS.add(new Rep(G5, "فواتير المشتريات للفترة", p -> invoices(p, InvType.PURCHASE)).party(Model.SUPPLIER, false).payType());
        REPS.add(new Rep(G5, "الخصومات على المشتريات (تفصيلي)", p -> ReportsStock.discountsDetail(p, InvType.PURCHASE)).party(Model.SUPPLIER, false).category());
        REPS.add(new Rep(G5, "تذبذب أسعار الشراء", ReportsMoney::priceFluctuation));
        REPS.add(new Rep(G5, "أرصدة الموردين", p -> balances(Model.SUPPLIER)).noDates());
        REPS.add(new Rep(G5, "أعمار ذمم الموردين", ReportsStock::agingSuppliers).noDates());
        REPS.add(new Rep(G5, "كشف حساب مورد", p -> statement(p, Model.SUPPLIER)).party(Model.SUPPLIER, true).details());
        REPS.add(new Rep(G5, "مصادقة رصيد مورد", p -> confirmation(p, Model.SUPPLIER)).party(Model.SUPPLIER, true).asOf());
        REPS.add(new Rep(G5, "فواتير الموردين - إجمالي", p -> invoicesByParty(p, Model.SUPPLIER)));
        REPS.add(new Rep(G5, "المتبقي للموردين (فواتير غير مسددة)", p -> remainingInvoices(p, Model.SUPPLIER)).noDates().party(Model.SUPPLIER, false));
        REPS.add(new Rep(G5, "إجمالي حسب الصنف لمورد", p -> itemsForParty(p, Model.SUPPLIER)).party(Model.SUPPLIER, true).category());
        REPS.add(new Rep(G5, "سندات الصرف", p -> vouchers(p, Model.SUPPLIER)).party(Model.SUPPLIER, false));
        REPS.add(new Rep(G5, "سندات القبض من مورد", p -> vouchersDir(p, Model.SUPPLIER, true)).party(Model.SUPPLIER, false));
        REPS.add(new Rep(G5, "حركة التسديد للموردين", p -> payments(p, Model.SUPPLIER)).party(Model.SUPPLIER, false));
        REPS.add(new Rep(G5, "حركة السداد للموردين حسب طريقة الدفع", p -> paymentsByMethod(p, Model.SUPPLIER)).party(Model.SUPPLIER, false));
        REPS.add(new Rep(G5, "مرتجعات المشتريات", p -> invoices(p, InvType.PURCHASE_RETURN)).party(Model.SUPPLIER, false));

        // ===== الصندوق والحسابات =====
        REPS.add(new Rep(G2, "الملخص اليومي", ReportsMoney::daily));
        REPS.add(new Rep(G2, "حركة الصندوق", ReportsMoney::cashMovement).wallet());
        REPS.add(new Rep(G2, "أرصدة المحافظ", ReportsMoney::walletBalances).noDates());
        REPS.add(new Rep(G2, "تسوية الصندوق اليومية", ReportsMoney::dailyCash).wallet());

        // ===== الضرائب =====
        REPS.add(new Rep(TX, "تقرير الضريبة", ReportsMoney::taxReport));
        REPS.add(new Rep(TX, "إجمالي الضرائب حسب الصنف", ReportsMoney::taxByItem).category());

        // ===== المصروفات =====
        REPS.add(new Rep(G3, "تقرير المصروفات", ReportsMoney::expenses));
        REPS.add(new Rep(G3, "المصروفات حسب البند", ReportsMoney::expensesByCat));

        // ===== المحاسبة =====
        REPS.add(new Rep(AC, "دفتر اليومية", AccReports::journal));
        REPS.add(new Rep(AC, "دفتر الأستاذ العام", AccReports::ledger).account());
        REPS.add(new Rep(AC, "ميزان المراجعة", AccReports::trial));
        REPS.add(new Rep(AC, "قائمة الدخل", AccReports::income));
        REPS.add(new Rep(AC, "الميزانية العمومية", AccReports::balanceSheet).asOf());

        // ===== رأس المال والزكاة =====
        REPS.add(new Rep(CZ, "تقرير رأس المال", ReportsUI::capital).noDates());
        REPS.add(new Rep(CZ, "حساب الزكاة", ReportsUI::zakat).noDates().zakat());
    }
    // ===================== التقارير =====================
    static boolean in(LocalDateTime d, P p) { return Ui.in(d, p.from, p.to); }

    // ===================== أدوات حساب الأرباح =====================
    /** لكل سطر: {الإيراد بدون ضريبة، التكلفة، الكمية بالحبة، المبلغ المدفوع شامل الضريبة} */
    static double[] amounts(Invoice i, InvLine l) {
        double ratio = i.subtotal == 0 ? 1 : (i.subtotal - i.discount) / i.subtotal;
        double net = l.total() * ratio, taxShare = 0;
        if (i.tax > 0 && l.taxable) {
            double taxable = 0; for (InvLine x : i.lines) if (x.taxable) taxable += x.total() * ratio;
            taxShare = taxable == 0 ? 0 : i.tax * net / taxable;
        }
        boolean incl = Math.abs(i.total - (i.subtotal - i.discount)) < 0.0005;
        return new double[]{incl ? net - taxShare : net, l.cost * l.pieces(), l.pieces(), incl ? net : net + taxShare};
    }
    static double invRevenue(Invoice i) { double r = 0; for (InvLine l : i.lines) r += amounts(i, l)[0]; return r; }
    static int sign(Invoice i) { return i.type == InvType.SALE ? 1 : i.type == InvType.SALE_RETURN ? -1 : 0; }
    static boolean saleLike(Invoice i, P p) { return !i.cancelled && sign(i) != 0 && in(i.date, p); }
    static String pct(double part, double whole) { return Math.abs(whole) < 0.0005 ? "" : Ui.q(Ui.r(part / whole * 100, 1)) + "%"; }
    static String payText(Invoice i) {
        if (i.paid < 0.0005) return "آجل";
        String w = i.paid2 > 0.0005 ? (i.paid - i.paid2 > 0.0005 ? db().walletName(i.walletId) + " + " : "") + db().walletName(i.wallet2) : db().walletName(i.walletId);
        return i.paid + 0.0005 < i.total ? "جزئي (" + w + ")" : w;
    }

    // ===================== تقارير المبيعات =====================
    /** mode: all / credit / pay / nos */
    static Result sales(P p, String mode) {
        Result r = new Result("رقم الفاتورة", "التاريخ", "العميل", "طريقة الدفع", "الأصناف", "الخصم", "الضريبة", "الإجمالي", "المدفوع", "آجل عند البيع", "المتبقي حالياً", "الربح", "الحالة");
        double tot = 0, paid = 0, credit = 0, rem = 0, profit = 0; int n = 0;
        Map<String, Double> byPay = new LinkedHashMap<>();
        for (Invoice i : db().invoices) {
            if (i.type != InvType.SALE) continue;
            if (mode.equals("nos")) { if (i.no < p.noFrom || i.no > p.noTo) continue; }
            else if (!in(i.date, p)) continue;
            if (p.party != 0 && i.partyId != p.party) continue;
            boolean isCredit = i.total - i.paid > 0.0005;
            if (mode.equals("credit") && !isCredit) continue;
            if (p.payType.equals("credit") && !isCredit) continue;
            if (p.payType.startsWith("w:")) { long w = Long.parseLong(p.payType.substring(2));
                boolean m1 = i.walletId == w && i.paid - i.paid2 > 0.0005, m2 = i.wallet2 == w && i.paid2 > 0.0005; if (!m1 && !m2) continue; }
            double pr = invRevenue(i) - i.costTotal(), rm = Ops.remaining(i);
            r.addInv(i.id, i.no, Ui.dt(i.date), db().partyName(i.partyId), payText(i), i.lines.size(), i.discount, i.tax, i.total, i.paid,
                    Ui.r(i.total - i.paid), rm, Ui.r(pr), i.cancelled ? "ملغاة" : "فعّالة");
            if (i.cancelled) continue;
            n++; tot += i.total; paid += i.paid; credit += i.total - i.paid; rem += rm; profit += pr;
            if (i.paid - i.paid2 > 0.0005) byPay.merge(db().walletName(i.walletId), i.paid - i.paid2, Double::sum);
            if (i.paid2 > 0.0005) byPay.merge(db().walletName(i.wallet2), i.paid2, Double::sum);
            if (isCredit) byPay.merge("آجل", i.total - i.paid, Double::sum);
        }
        StringBuilder sb = new StringBuilder("الفواتير: " + n + "  |  الإجمالي: " + Ui.m(tot) + "  |  المقبوض: " + Ui.m(paid) + "  |  الآجل: " + Ui.m(credit));
        if (mode.equals("credit")) sb.append("  |  المتبقي حالياً بعد التسديدات: ").append(Ui.m(rem));
        sb.append("  |  الربح: ").append(Ui.m(profit));
        if (mode.equals("pay") || mode.equals("all")) { sb.append("\n"); byPay.forEach((k, v) -> sb.append("   ").append(k).append(": ").append(Ui.m(v))); }
        r.summary = "<html>" + Ui.esc(sb.toString()).replace("\n", "<br>") + "</html>";
        return r;
    }

    static Result salesByCustomer(P p) {
        Result r = new Result("العميل", "عدد الفواتير", "المبيعات", "المرتجعات", "صافي المبيعات", "المقبوض عند البيع", "الآجل", "الرصيد الحالي", "الربح", "النسبة من المبيعات");
        Map<Long, double[]> m = new LinkedHashMap<>(); double total = 0;
        for (Invoice i : db().invoices) if (saleLike(i, p)) {
            double[] a = m.computeIfAbsent(i.partyId, k -> new double[6]);
            if (sign(i) > 0) { a[0]++; a[1] += i.total; a[3] += i.paid; a[4] += i.total - i.paid; } else a[2] += i.total;
            a[5] += sign(i) * (invRevenue(i) - i.costTotal());
        }
        for (double[] a : m.values()) total += a[1] - a[2];
        Map<Long, Double> bal = Ops.allBalances(Model.CUSTOMER);
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(y.getValue()[1] - y.getValue()[2], x.getValue()[1] - x.getValue()[2]));
        for (Map.Entry<Long, double[]> e : es) { double[] a = e.getValue();
            r.add(db().partyName(e.getKey()), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[1] - a[2]), Ui.r(a[3]), Ui.r(a[4]), e.getKey() == 0 ? "" : (Object) bal.getOrDefault(e.getKey(), 0.0), Ui.r(a[5]), pct(a[1] - a[2], total)); }
        r.summary = "عدد العملاء: " + r.rows.size() + "  |  صافي المبيعات: " + Ui.m(total); return r;
    }

    /** حسب التصنيف (مع إمكانية اختيار تصنيف واحد لعرض أصنافه) */
    static Result byCategory(P p, boolean profitSort) {
        if (p.category != 0) { Result r = byItem(p, profitSort); r.summary = db().categoryName(p.category) + "  |  " + r.summary; return r; }
        Result r = new Result("التصنيف", "عدد الأصناف المباعة", "الكمية (حبة)", "صافي المبيعات (بدون ضريبة)", "التكلفة", "الربح", "هامش الربح", "النسبة من المبيعات");
        Map<Long, double[]> m = new LinkedHashMap<>(); Map<Long, Set<Long>> items = new HashMap<>(); double tr = 0, tp = 0;
        for (Invoice i : db().invoices) if (saleLike(i, p)) for (InvLine l : i.lines) {
            Product x = db().products.get(l.productId); long cat = x == null ? 0 : x.categoryId;
            double[] am = amounts(i, l), a = m.computeIfAbsent(cat, k -> new double[3]); int sg = sign(i);
            a[0] += sg * am[2]; a[1] += sg * am[0]; a[2] += sg * am[1]; items.computeIfAbsent(cat, k -> new HashSet<>()).add(l.productId);
        }
        for (double[] a : m.values()) { tr += a[1]; tp += a[1] - a[2]; }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet());
        es.sort((x, y) -> profitSort ? Double.compare(y.getValue()[1] - y.getValue()[2], x.getValue()[1] - x.getValue()[2]) : Double.compare(y.getValue()[1], x.getValue()[1]));
        for (Map.Entry<Long, double[]> e : es) { double[] a = e.getValue(); String cn = db().categoryName(e.getKey());
            r.add(cn.isEmpty() ? "بدون تصنيف" : cn, (long) items.get(e.getKey()).size(), Ui.q(a[0]), Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[1] - a[2]), pct(a[1] - a[2], a[1]), pct(a[1], tr)); }
        r.summary = "صافي المبيعات: " + Ui.m(tr) + "  |  الربح: " + Ui.m(tp) + "  |  هامش الربح: " + pct(tp, tr); return r;
    }

    static Result offerSales(P p) {
        Result r = new Result("العرض", "الصنف", "عدد الفواتير", "الكمية المباعة", "قيمة المبيعات", "إجمالي الخصم (ما وفّره الزبائن)");
        Map<String, Object[]> m = new LinkedHashMap<>(); Map<String, Set<Long>> invs = new HashMap<>();
        for (Invoice i : db().invoices) {
            if (i.type != InvType.SALE || i.cancelled || !in(i.date, p)) continue;
            for (InvLine l : i.lines) {
                if (l.offer == null || l.offer.isEmpty()) continue;
                Object[] a = m.computeIfAbsent(l.offer + "|" + l.productId, k -> new Object[]{l.offer, l.name, 0.0, 0.0, 0.0});
                a[2] = (double) a[2] + l.pieces(); a[3] = (double) a[3] + l.total(); a[4] = (double) a[4] + l.discount;
                invs.computeIfAbsent(l.offer + "|" + l.productId, k -> new HashSet<>()).add(i.id);
            }
        }
        double disc = 0, val = 0;
        for (Map.Entry<String, Object[]> e : m.entrySet()) { Object[] a = e.getValue();
            r.add(a[0], a[1], (long) invs.get(e.getKey()).size(), Ui.q((double) a[2]), Ui.r((double) a[3]), Ui.r((double) a[4]));
            disc += (double) a[4]; val += (double) a[3]; }
        r.summary = "عدد العروض المباعة: " + m.size() + "  |  مبيعات العروض: " + Ui.m(val) + "  |  إجمالي ما وفّره الزبائن: " + Ui.m(disc);
        return r;
    }

    static Result byItem(P p, boolean profitSort) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية (حبة)", "صافي المبيعات (بدون ضريبة)", "التكلفة", "الربح", "هامش الربح", "متوسط سعر البيع");
        Map<Long, double[]> m = new HashMap<>();
        for (Invoice i : db().invoices) if (saleLike(i, p)) for (InvLine l : i.lines) {
            Product x = db().products.get(l.productId);
            if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
            double[] am = amounts(i, l), a = m.computeIfAbsent(l.productId, k -> new double[3]); int sg = sign(i);
            a[0] += sg * am[2]; a[1] += sg * am[0]; a[2] += sg * am[1];
        }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet());
        es.sort((x, y) -> profitSort ? Double.compare(y.getValue()[1] - y.getValue()[2], x.getValue()[1] - x.getValue()[2]) : Double.compare(y.getValue()[1], x.getValue()[1]));
        double ts = 0, tp = 0, tq = 0;
        for (Map.Entry<Long, double[]> e : es) { Product x = db().products.get(e.getKey()); double[] a = e.getValue();
            r.add(x == null ? "" : x.barcode, x == null ? "صنف محذوف" : x.name, x == null ? "" : db().categoryName(x.categoryId), Ui.q(a[0]), Ui.r(a[1]), Ui.r(a[2]),
                    Ui.r(a[1] - a[2]), pct(a[1] - a[2], a[1]), a[0] == 0 ? "" : (Object) Ui.r(a[1] / a[0]));
            ts += a[1]; tp += a[1] - a[2]; tq += a[0]; }
        r.summary = "الأصناف: " + r.rows.size() + "  |  الكمية: " + Ui.q(tq) + "  |  صافي المبيعات: " + Ui.m(ts) + "  |  الربح: " + Ui.m(tp) + "  |  الهامش: " + pct(tp, ts);
        return r;
    }

    static Result profitByCustomer(P p) {
        Result r = new Result("العميل", "عدد الفواتير", "صافي المبيعات (بدون ضريبة)", "التكلفة", "الربح", "هامش الربح", "النسبة من الأرباح");
        Map<Long, double[]> m = new LinkedHashMap<>(); double tp = 0, tr = 0;
        for (Invoice i : db().invoices) if (saleLike(i, p)) {
            double[] a = m.computeIfAbsent(i.partyId, k -> new double[3]); int sg = sign(i);
            if (sg > 0) a[0]++; a[1] += sg * invRevenue(i); a[2] += sg * i.costTotal();
        }
        for (double[] a : m.values()) { tp += a[1] - a[2]; tr += a[1]; }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(y.getValue()[1] - y.getValue()[2], x.getValue()[1] - x.getValue()[2]));
        for (Map.Entry<Long, double[]> e : es) { double[] a = e.getValue();
            r.add(db().partyName(e.getKey()), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[1] - a[2]), pct(a[1] - a[2], a[1]), pct(a[1] - a[2], tp)); }
        r.summary = "إجمالي الربح: " + Ui.m(tp) + "  |  صافي المبيعات: " + Ui.m(tr) + "  |  الهامش: " + pct(tp, tr); return r;
    }

    /** المبيعات والأرباح يوماً بيوم مع الإجمالي */
    static Result salesProfitDaily(P p) {
        Result r = new Result("التاريخ", "عدد الفواتير", "المبيعات", "المرتجعات", "صافي المبيعات (بدون ضريبة)", "التكلفة", "الربح", "هامش الربح");
        TreeMap<LocalDate, double[]> d = new TreeMap<>(); // n, sales, returns, rev, cost
        for (Invoice i : db().invoices) if (saleLike(i, p)) {
            double[] a = d.computeIfAbsent(i.date.toLocalDate(), k -> new double[5]); int sg = sign(i);
            if (sg > 0) { a[0]++; a[1] += i.total; } else a[2] += i.total;
            a[3] += sg * invRevenue(i); a[4] += sg * i.costTotal();
        }
        double[] t = new double[5];
        for (Map.Entry<LocalDate, double[]> e : d.entrySet()) { double[] a = e.getValue(); for (int k = 0; k < 5; k++) t[k] += a[k];
            r.add(e.getKey().toString(), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[3]), Ui.r(a[4]), Ui.r(a[3] - a[4]), pct(a[3] - a[4], a[3])); }
        if (!d.isEmpty()) r.add("الإجمالي", (long) t[0], Ui.r(t[1]), Ui.r(t[2]), Ui.r(t[3]), Ui.r(t[4]), Ui.r(t[3] - t[4]), pct(t[3] - t[4], t[3]));
        r.summary = "الأيام: " + d.size() + "  |  الربح: " + Ui.m(t[3] - t[4]) + "  |  الهامش: " + pct(t[3] - t[4], t[3])
                + (d.isEmpty() ? "" : "  |  متوسط الربح اليومي: " + Ui.m((t[3] - t[4]) / d.size()));
        return r;
    }

    /** أرباح فواتير المبيعات ضمن مدى أرقام */
    static Result profitByInvoiceNo(P p) {
        Result r = new Result("رقم الفاتورة", "التاريخ", "العميل", "الإجمالي", "صافي بدون ضريبة", "التكلفة", "الربح", "هامش الربح");
        double tp = 0, tr = 0, tt = 0; List<Invoice> l = new ArrayList<>();
        for (Invoice i : db().invoices) if (!i.cancelled && i.type == InvType.SALE && i.no >= p.noFrom && i.no <= p.noTo) l.add(i);
        l.sort(Comparator.comparingLong(i -> i.no));
        for (Invoice i : l) { double rev = invRevenue(i), cost = i.costTotal();
            r.addInv(i.id, i.no, Ui.dt(i.date), db().partyName(i.partyId), i.total, Ui.r(rev), Ui.r(cost), Ui.r(rev - cost), pct(rev - cost, rev)); tp += rev - cost; tr += rev; tt += i.total; }
        if (!l.isEmpty()) r.add("الإجمالي", "", "", Ui.r(tt), Ui.r(tr), Ui.r(tr - tp), Ui.r(tp), pct(tp, tr));
        r.summary = "من رقم " + p.noFrom + " إلى " + p.noTo + "  |  الفواتير: " + l.size() + "  |  الربح: " + Ui.m(tp) + "  |  الهامش: " + pct(tp, tr) + "  (انقر مرتين لمعاينة الفاتورة)";
        return r;
    }

    /** أرباح أصناف تصنيف واحد */
    static Result profitOfCategory(P p) {
        if (p.category == 0) { Result r = new Result("الباركود", "الصنف", "الكمية", "الربح"); r.summary = "اختر التصنيف من الأعلى ثم اضغط عرض"; return r; }
        Result r = byItem(p, true); r.summary = "التصنيف: " + db().categoryName(p.category) + "  |  " + r.summary; return r;
    }

    /** أرباح كل فرع للفترة: هذا الفرع من بياناته مباشرة، والفروع الأخرى من ملفاتها في المجلد المشترك */
    static Result profitByBranch(P p) {
        Result r = new Result("الفرع", "عدد الفواتير", "المبيعات", "المرتجعات", "صافي المبيعات", "الربح", "هامش الربح", "النسبة من الأرباح", "آخر تحديث");
        List<Object[]> rows = new ArrayList<>(); double[] t = new double[4];
        // هذا الفرع
        double n = 0, sl = 0, rt = 0, pr = 0;
        for (Invoice i : db().invoices) if (saleLike(i, p)) { int sg = sign(i); if (sg > 0) { n++; sl += i.total; } else rt += i.total; pr += sg * (invRevenue(i) - i.costTotal()); }
        rows.add(new Object[]{Branches.name() + " (هذا الفرع)", n, sl, rt, pr, "الآن"});
        boolean on = Branches.on(); String mine = on ? Branches.code() : "";
        if (on) for (Branches.Snap s : Branches.read()) { if (s.code != null && s.code.equals(mine)) continue;
            LocalDate a = p.from == null ? LocalDate.of(2000, 1, 1) : p.from, b = p.to == null ? LocalDate.now() : p.to;
            rows.add(new Object[]{s.name, s.sales(a, b, 2), s.sales(a, b, 0), s.sales(a, b, 1), s.sales(a, b, 3), Branches.age(s.updated)}); }
        for (Object[] x : rows) { t[0] += (double) x[1]; t[1] += (double) x[2]; t[2] += (double) x[3]; t[3] += (double) x[4]; }
        rows.sort((x, y) -> Double.compare((double) y[4], (double) x[4]));
        for (Object[] x : rows) { double net = (double) x[2] - (double) x[3], pf = (double) x[4];
            r.add(x[0], (long) (double) x[1], Ui.r((double) x[2]), Ui.r((double) x[3]), Ui.r(net), Ui.r(pf), pct(pf, net), pct(pf, t[3]), x[5]); }
        if (rows.size() > 1) r.add("الإجمالي", (long) t[0], Ui.r(t[1]), Ui.r(t[2]), Ui.r(t[1] - t[2]), Ui.r(t[3]), pct(t[3], t[1] - t[2]), "100%", "");
        r.summary = (on ? "عدد الفروع: " + rows.size() : "ربط الفروع غير مفعّل — يُعرض هذا الفرع فقط (فعّله من الإعدادات ← الفروع)")
                + "  |  إجمالي الربح: " + Ui.m(t[3]) + (on && rows.size() > 1 ? "  |  بيانات الفروع الأخرى متاحة لآخر 120 يوماً" : "");
        return r;
    }

    static Result profitByInvoice(P p) {
        Result r = new Result("النوع", "رقم الفاتورة", "التاريخ", "العميل", "الإجمالي", "صافي بدون ضريبة", "التكلفة", "الربح", "هامش الربح");
        double tp = 0, tr = 0;
        for (Invoice i : db().invoices) if (saleLike(i, p) && (p.party == 0 || i.partyId == p.party)) {
            int sg = sign(i); double rev = sg * invRevenue(i), cost = sg * i.costTotal();
            r.addInv(i.id, sg > 0 ? "مبيعات" : "مرتجع", i.no, Ui.dt(i.date), db().partyName(i.partyId), sg * i.total, Ui.r(rev), Ui.r(cost), Ui.r(rev - cost), pct(rev - cost, rev));
            tp += rev - cost; tr += rev;
        }
        r.summary = "الفواتير: " + r.rows.size() + "  |  الربح: " + Ui.m(tp) + "  |  الهامش: " + pct(tp, tr) + "  (انقر مرتين لمعاينة الفاتورة)"; return r;
    }

    // ===================== حركة السداد =====================
    static Result payments(P p, int kind) {
        Result r = new Result("التاريخ", kind == Model.CUSTOMER ? "العميل" : "المورد", "نوع الحركة", "رقم المستند", "المبلغ", "المحفظة / طريقة الدفع", "البيان", "رصيد الحساب الحالي");
        List<Object[]> rows = new ArrayList<>(); List<LocalDateTime> dates = new ArrayList<>(); List<Long> ids = new ArrayList<>();
        InvType main = kind == Model.CUSTOMER ? InvType.SALE : InvType.PURCHASE, ret = kind == Model.CUSTOMER ? InvType.SALE_RETURN : InvType.PURCHASE_RETURN;
        Map<Long, Double> bal = Ops.allBalances(kind); double in = 0, out = 0;
        for (Invoice i : db().invoices) {
            if (i.cancelled || i.partyId == 0 || !in(i.date, p) || (p.party != 0 && i.partyId != p.party) || i.paid < 0.0005) continue;
            if (i.type != main && i.type != ret) continue;
            boolean pay = i.type == main; double amt = pay ? i.paid : -i.paid;
            rows.add(new Object[]{Ui.dt(i.date), db().partyName(i.partyId), pay ? "دفعة عند " + (kind == Model.CUSTOMER ? "البيع" : "الشراء") : "مبلغ مُرجع مع " + i.type.title,
                    i.type.title + " " + i.no, Ui.r(amt), payText(i), i.notes, bal.getOrDefault(i.partyId, 0.0)});
            dates.add(i.date); ids.add(i.id); if (amt > 0) in += amt; else out -= amt;
        }
        for (Voucher v : db().vouchers) {
            if (v.cancelled || v.partyKind != kind || !in(v.date, p) || (p.party != 0 && v.partyId != p.party)) continue;
            boolean pay = kind == Model.CUSTOMER ? v.in : !v.in; double amt = pay ? v.amount : -v.amount;
            Invoice ref = v.invoiceId == 0 ? null : db().invoice(v.invoiceId);
            rows.add(new Object[]{Ui.dt(v.date), db().partyName(v.partyId), (v.in ? "سند قبض" : "سند صرف") + (ref != null ? " - تسديد فاتورة " + ref.no : ""),
                    String.valueOf(v.no), Ui.r(amt), db().walletName(v.walletId), v.notes, bal.getOrDefault(v.partyId, 0.0)});
            dates.add(v.date); ids.add(ref == null ? null : ref.id); if (amt > 0) in += amt; else out -= amt;
        }
        Integer[] idx = new Integer[rows.size()]; for (int k = 0; k < idx.length; k++) idx[k] = k;
        Arrays.sort(idx, (a, b) -> dates.get(a).compareTo(dates.get(b)));
        for (int k : idx) { r.rows.add(rows.get(k)); r.inv.add(ids.get(k)); }
        r.summary = (kind == Model.CUSTOMER ? "المقبوض من العملاء: " : "المدفوع للموردين: ") + Ui.m(in) + "  |  المُرجع: " + Ui.m(out) + "  |  الصافي: " + Ui.m(in - out) + "  |  عدد الحركات: " + r.rows.size();
        return r;
    }

    // ===================== رأس المال والزكاة =====================
    static double[] assets() {
        double cash = 0; for (Wallet w : db().wallets) cash += Ops.walletBalance(w.id);
        double invCost = 0, invSell = 0; for (Product x : db().products.values()) if (x.qty > 0) { invCost += x.qty * x.cost; invSell += x.qty * x.price; }
        double rec = 0, custCredit = 0; for (double v : Ops.allBalances(Model.CUSTOMER).values()) { if (v > 0) rec += v; else custCredit -= v; }
        double pay = 0, supAdv = 0; for (double v : Ops.allBalances(Model.SUPPLIER).values()) { if (v > 0) pay += v; else supAdv -= v; }
        return new double[]{cash, invCost, invSell, rec, custCredit, pay, supAdv};
    }

    static Result capital(P p) {
        Result r = new Result("البند", "المبلغ", "ملاحظة");
        double[] a = assets();
        r.add("أولاً: الأصول", "", "");
        for (Wallet w : db().wallets) { double b = Ops.walletBalance(w.id); if (Math.abs(b) > 0.0005) r.add("   " + w.name, Ui.r(b), w.type); }
        r.add("   إجمالي النقدية", Ui.r(a[0]), "");
        r.add("   البضاعة في المخزون (بسعر التكلفة)", Ui.r(a[1]), "بسعر البيع: " + Ui.m(a[2]));
        r.add("   ذمم العملاء (مستحق لنا)", Ui.r(a[3]), "");
        r.add("   دفعات مقدمة للموردين", Ui.r(a[6]), "");
        double assets = a[0] + a[1] + a[3] + a[6];
        r.add("إجمالي الأصول", Ui.r(assets), "");
        r.add("ثانياً: الالتزامات", "", "");
        r.add("   ذمم الموردين (مستحق علينا)", Ui.r(a[5]), "");
        r.add("   أرصدة دائنة للعملاء", Ui.r(a[4]), "");
        double liab = a[5] + a[4];
        r.add("إجمالي الالتزامات", Ui.r(liab), "");
        r.add("صافي رأس المال (الأصول - الالتزامات)", Ui.r(assets - liab), "قيمة المحل الحالية");
        r.summary = "صافي رأس المال: " + Ui.m(assets - liab) + "  |  منها نقدية " + pct(a[0], assets) + " وبضاعة " + pct(a[1], assets) + " وذمم " + pct(a[3], assets);
        return r;
    }

    static Result zakat(P p) {
        Result r = new Result("البند", "المبلغ", "ملاحظة");
        double[] a = assets();
        double inv = p.atSelling ? a[2] : a[1];
        r.add("النقدية في الصناديق والبنوك والمحافظ", Ui.r(a[0]), "");
        r.add("عروض التجارة (البضاعة)", Ui.r(inv), p.atSelling ? "مقيّمة بسعر البيع الحالي" : "مقيّمة بسعر التكلفة");
        if (p.includeRec) r.add("ديون العملاء المرجوّ تحصيلها", Ui.r(a[3]), "استبعد الديون المشكوك فيها بإلغاء الخيار");
        r.add("دفعات مقدمة للموردين", Ui.r(a[6]), "");
        double gross = a[0] + inv + (p.includeRec ? a[3] : 0) + a[6];
        r.add("إجمالي الأموال الزكوية", Ui.r(gross), "");
        r.add("يُطرح: الديون المستحقة على المحل", Ui.r(-(a[5] + a[4])), "ذمم الموردين وأرصدة العملاء الدائنة");
        double base = gross - a[5] - a[4];
        r.add("وعاء الزكاة", Ui.r(base), "");
        double nisab = p.goldPrice * 85;
        r.add("النصاب (85 غرام ذهب عيار 24)", p.goldPrice > 0 ? (Object) Ui.r(nisab) : "أدخل سعر الذهب", p.goldPrice > 0 ? "سعر الغرام " + Ui.m(p.goldPrice) : "");
        double rate = p.solar ? 0.02577 : 0.025;
        boolean due = base > 0 && (p.goldPrice <= 0 || base >= nisab);
        double z = due ? base * rate : 0;
        r.add("نسبة الزكاة", Ui.q(rate * 100) + "%", p.solar ? "عند اعتماد السنة الميلادية" : "عند اعتماد السنة الهجرية");
        r.add("مقدار الزكاة الواجبة", Ui.r(z), base <= 0 ? "لا زكاة: الوعاء غير موجب" : p.goldPrice > 0 && base < nisab ? "لم يبلغ النصاب" : "بلغ النصاب");
        r.add("", "", "");
        r.add("تنبيه: الحساب تقديري حسب بيانات البرنامج في هذا اليوم، ويُشترط مرور حول كامل على بلوغ النصاب.", "", "");
        r.add("يُنصح بمراجعة جهة شرعية موثوقة لاعتماد المبلغ النهائي.", "", "");
        r.summary = "وعاء الزكاة: " + Ui.m(base) + "  |  الزكاة الواجبة: " + Ui.m(z) + " " + db().get("currency", "");
        return r;
    }
}
