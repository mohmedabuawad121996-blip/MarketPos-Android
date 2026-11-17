package com.market;

import com.market.Model.*;
import static com.market.ReportsUI.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** تقارير العملاء والموردين: كشوف الحساب والسندات والفواتير والمصادقة */
final class ReportsParties {
    private ReportsParties() {}
    static Result statement(P p, int kind) {
        Party party = db().party(p.party);
        Result r = new Result("التاريخ", "البيان", "رقم المستند", "مدين", "دائن", "الرصيد");
        double bal = 0, td = 0, tc = 0; List<Ledger> l = new ArrayList<>();
        for (Ledger x : db().ledger) if (x.partyKind == kind && x.partyId == p.party) {
            double v = kind == Model.CUSTOMER ? x.debit - x.credit : x.credit - x.debit;
            if (x.date.isBefore(Ui.start(p.from))) bal += v; else if (x.date.isBefore(Ui.end(p.to))) l.add(x);
        }
        l.sort(Comparator.comparing(x -> x.date));
        r.add("", "رصيد سابق", "", "", "", Ui.r(bal));
        for (Ledger x : l) {
            bal += kind == Model.CUSTOMER ? x.debit - x.credit : x.credit - x.debit; td += x.debit; tc += x.credit;
            String no = "";
            Invoice inv = null;
            if (x.ref.equals("SALE") || x.ref.equals("PURCHASE") || x.ref.endsWith("RETURN")) { inv = db().invoice(x.refId); if (inv != null) no = String.valueOf(inv.no); }
            else if (x.ref.equals("RECEIPT") || x.ref.equals("PAYMENT")) { for (Voucher v : db().vouchers) if (v.id == x.refId) no = String.valueOf(v.no); }
            Object[] row = {Ui.dt(x.date), x.notes, no, x.debit == 0 ? "" : (Object) x.debit, x.credit == 0 ? "" : (Object) x.credit, Ui.r(bal)};
            if (inv != null) r.addInv(inv.id, row); else r.add(row);
            if (p.details && inv != null && !x.notes.contains("(إلغاء)"))
                for (InvLine il : inv.lines) r.add("", "      - " + il.name + " [" + il.unit + "]:  " + Ui.m(il.price) + " × " + Ui.q(il.qty) + " = " + Ui.m(il.total()), "", "", "", "");
        }
        r.add("", "الإجمالي", "", Ui.r(td), Ui.r(tc), Ui.r(bal));
        String side = kind == Model.CUSTOMER ? (bal >= 0 ? "مدين لنا" : "دائن (له)") : (bal >= 0 ? "مستحق للمورد" : "مدين لنا");
        r.summary = party.name + "  |  الرصيد الختامي: " + Ui.m(Math.abs(bal)) + " " + side + (party.phone.trim().isEmpty() ? "" : "  |  هاتف: " + party.phone);
        return r;
    }

    static Result vouchers(P p, int kind) {
        Result r = new Result("رقم السند", "النوع", "التاريخ", PartyUI.noun(kind), "المبلغ", "المحفظة", "البيان", "الحالة"); double s = 0;
        for (Voucher v : db().vouchers) if (v.partyKind == kind && in(v.date, p) && (p.party == 0 || v.partyId == p.party)) {
            r.add(v.no, v.in ? "قبض" : "صرف", Ui.dt(v.date), db().partyName(v.partyId), v.amount, db().walletName(v.walletId), v.notes, v.cancelled ? "ملغى" : "فعّال");
            if (!v.cancelled) s += v.in ? v.amount : -v.amount; }
        r.summary = "عدد السندات: " + r.rows.size() + "  |  الصافي (قبض - صرف): " + Ui.m(s); return r;
    }

    /** سندات اتجاه واحد فقط (قبض أو صرف) */
    static Result vouchersDir(P p, int kind, boolean in) {
        Result r = new Result("رقم السند", "التاريخ", PartyUI.noun(kind), "المبلغ", "المحفظة", "البيان", "الحالة"); double s = 0; int n = 0;
        for (Voucher v : db().vouchers) if (v.partyKind == kind && v.in == in && in(v.date, p) && (p.party == 0 || v.partyId == p.party)) {
            r.add(v.no, Ui.dt(v.date), db().partyName(v.partyId), v.amount, db().walletName(v.walletId), v.notes, v.cancelled ? "ملغى" : "فعّال");
            if (!v.cancelled) { s += v.amount; n++; } }
        if (n > 0) r.add("", "الإجمالي", "", Ui.r(s), "", "", "");
        r.summary = "عدد السندات الفعّالة: " + n + "  |  الإجمالي: " + Ui.m(s); return r;
    }

    /** سطر لكل عميل/مورد: عدد الفواتير والمبيعات والمرتجعات والمدفوع والمتبقي والرصيد الحالي */
    static Result invoicesByParty(P p, int kind) {
        InvType main = kind == Model.CUSTOMER ? InvType.SALE : InvType.PURCHASE, ret = kind == Model.CUSTOMER ? InvType.SALE_RETURN : InvType.PURCHASE_RETURN;
        Result r = new Result(PartyUI.noun(kind), "عدد الفواتير", kind == Model.CUSTOMER ? "المبيعات" : "المشتريات", "المرتجعات", "الصافي", "المدفوع عند الفاتورة", "المتبقي من الفواتير", "الرصيد الحالي");
        Map<Long, double[]> m = new LinkedHashMap<>(); Map<Long, Double> bal = Ops.allBalances(kind);
        for (Invoice i : db().invoices) if (!i.cancelled && i.partyId != 0 && in(i.date, p) && (i.type == main || i.type == ret)) {
            double[] a = m.computeIfAbsent(i.partyId, k -> new double[5]);
            if (i.type == main) { a[0]++; a[1] += i.total; a[3] += i.paid; a[4] += Ops.remaining(i); } else a[2] += i.total; }
        double[] t = new double[5];
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(y.getValue()[1], x.getValue()[1]));
        for (Map.Entry<Long, double[]> e : es) { double[] a = e.getValue(); for (int k = 0; k < 5; k++) t[k] += a[k];
            r.add(db().partyName(e.getKey()), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[1] - a[2]), Ui.r(a[3]), Ui.r(a[4]), new Ui.Bal(kind, bal.getOrDefault(e.getKey(), 0.0))); }
        if (!m.isEmpty()) r.add("الإجمالي", (long) t[0], Ui.r(t[1]), Ui.r(t[2]), Ui.r(t[1] - t[2]), Ui.r(t[3]), Ui.r(t[4]), "");
        r.summary = "عدد " + (kind == Model.CUSTOMER ? "العملاء" : "الموردين") + ": " + m.size() + "  |  الفواتير: " + (long) t[0] + "  |  الصافي: " + Ui.m(t[1] - t[2]) + "  |  المتبقي من الفواتير: " + Ui.m(t[4]) + "  (الفواتير النقدية بدون عميل غير محسوبة)";
        return r;
    }

    /** الأصناف التي اشتراها عميل خلال الفترة: الكمية والقيمة ومتوسط السعر */
    static Result itemsForCustomer(P p) { return itemsForParty(p, Model.CUSTOMER); }

    /** الأصناف المتبادلة مع عميل (مبيعات) أو مورد (مشتريات) خلال الفترة */
    static Result itemsForParty(P p, int kind) {
        boolean cust = kind == Model.CUSTOMER;
        InvType main = cust ? InvType.SALE : InvType.PURCHASE, ret = cust ? InvType.SALE_RETURN : InvType.PURCHASE_RETURN;
        Result r = new Result("الباركود", "الصنف", "التصنيف", "عدد الفواتير", "الكمية (حبة)", "الإجمالي", "متوسط السعر", cust ? "آخر شراء" : "آخر توريد");
        Map<Long, double[]> m = new HashMap<>(); Map<Long, LocalDateTime> last = new HashMap<>(); Map<Long, Set<Long>> invs = new HashMap<>();
        for (Invoice i : db().invoices) if (!i.cancelled && in(i.date, p) && i.partyId == p.party && (i.type == main || i.type == ret)) for (InvLine l : i.lines) {
            Product x = db().products.get(l.productId);
            if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
            int sg = i.type == main ? 1 : -1; double[] a = m.computeIfAbsent(l.productId, k -> new double[2]);
            a[0] += sg * l.pieces(); a[1] += sg * l.total();
            if (sg > 0) { invs.computeIfAbsent(l.productId, k -> new HashSet<>()).add(i.id); last.merge(l.productId, i.date, (u, v) -> u.isAfter(v) ? u : v); }
        }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(y.getValue()[1], x.getValue()[1]));
        double tq = 0, tv = 0;
        for (Map.Entry<Long, double[]> e : es) { Product x = db().products.get(e.getKey()); double[] a = e.getValue(); tq += a[0]; tv += a[1];
            r.add(x == null ? "" : x.barcode, x == null ? "صنف محذوف" : x.name, x == null ? "" : db().categoryName(x.categoryId), (long) invs.getOrDefault(e.getKey(), Set.of()).size(),
                    Ui.q(a[0]), Ui.r(a[1]), a[0] == 0 ? "" : (Object) Ui.r(a[1] / a[0]), last.containsKey(e.getKey()) ? Ui.dt(last.get(e.getKey())) : ""); }
        if (!m.isEmpty()) r.add("", "الإجمالي", "", "", Ui.q(tq), Ui.r(tv), "", "");
        Party c = db().party(p.party);
        r.summary = (c == null ? "" : c.name + "  |  ") + "الأصناف: " + m.size() + "  |  الكمية: " + Ui.q(tq) + "  |  الإجمالي: " + Ui.m(tv);
        return r;
    }

    /** الفواتير غير المسددة بالكامل لكل عميل/مورد (بعد توزيع الدفعات العامة على الأقدم) */
    static Result remainingInvoices(P p, int kind) {
        InvType main = kind == Model.CUSTOMER ? InvType.SALE : InvType.PURCHASE;
        Result r = new Result(PartyUI.noun(kind), "رقم الفاتورة", "التاريخ", "عمر الفاتورة", "الإجمالي", "المدفوع", "المتبقي", "رصيد الحساب");
        Map<Long, Double> bal = Ops.allBalances(kind); List<Invoice> l = new ArrayList<>();
        for (Invoice i : db().invoices) if (!i.cancelled && i.type == main && i.partyId != 0 && (p.party == 0 || i.partyId == p.party) && Ops.remaining(i) > 0.0005) l.add(i);
        l.sort(Comparator.comparing((Invoice i) -> db().partyName(i.partyId)).thenComparing(i -> i.date));
        double tt = 0, tr = 0; Set<Long> parties = new HashSet<>(); long today = LocalDate.now().toEpochDay();
        for (Invoice i : l) { double rm = Ops.remaining(i); tt += i.total; tr += rm; parties.add(i.partyId);
            r.addInv(i.id, db().partyName(i.partyId), i.no, Ui.dt(i.date), (today - i.date.toLocalDate().toEpochDay()) + " يوم", i.total, Ui.r(i.total - rm), Ui.r(rm), new Ui.Bal(kind, bal.getOrDefault(i.partyId, 0.0))); }
        if (!l.isEmpty()) r.add("الإجمالي", "", "", "", Ui.r(tt), Ui.r(tt - tr), Ui.r(tr), "");
        r.summary = "الفواتير غير المسددة: " + l.size() + "  |  " + (kind == Model.CUSTOMER ? "العملاء" : "الموردين") + ": " + parties.size() + "  |  إجمالي المتبقي: " + Ui.m(tr) + "  (انقر مرتين لمعاينة الفاتورة)";
        return r;
    }

    /** حركة السداد مجمّعة حسب طريقة الدفع / المحفظة */
    static Result paymentsByMethod(P p, int kind) {
        Result base = payments(p, kind);
        Result r = new Result("طريقة الدفع / المحفظة", "عدد الحركات", kind == Model.CUSTOMER ? "المقبوض" : "المدفوع", "المُرجع", "الصافي", "النسبة");
        Map<String, double[]> m = new LinkedHashMap<>(); double tin = 0, tout = 0;
        for (Object[] row : base.rows) { String w = String.valueOf(row[5]); if (w.startsWith("جزئي (") && w.endsWith(")")) w = w.substring(6, w.length() - 1); double amt = row[4] instanceof Number n ? n.doubleValue() : 0;
            double[] a = m.computeIfAbsent(w.isEmpty() ? "—" : w, k -> new double[3]); a[0]++; if (amt > 0) { a[1] += amt; tin += amt; } else { a[2] -= amt; tout -= amt; } }
        double net = tin - tout;
        List<Map.Entry<String, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(y.getValue()[1] - y.getValue()[2], x.getValue()[1] - x.getValue()[2]));
        long cnt = 0;
        for (Map.Entry<String, double[]> e : es) { double[] a = e.getValue(); cnt += (long) a[0]; r.add(e.getKey(), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[1] - a[2]), pct(a[1] - a[2], net)); }
        if (!m.isEmpty()) r.add("الإجمالي", cnt, Ui.r(tin), Ui.r(tout), Ui.r(net), "100%");
        r.summary = (kind == Model.CUSTOMER ? "المقبوض: " : "المدفوع: ") + Ui.m(tin) + "  |  المُرجع: " + Ui.m(tout) + "  |  الصافي: " + Ui.m(net);
        return r;
    }

    /** مصادقة رصيد: الرصيد حتى تاريخ معيّن مع نص المصادقة وأماكن التوقيع (للطباعة وإرسالها للعميل/المورد) */
    static Result confirmation(P p, int kind) {
        Party c = db().party(p.party);
        Result r = new Result("البيان", "القيمة");
        if (c == null) { r.summary = "اختر " + PartyUI.noun(kind); return r; }
        LocalDate asOf = p.to == null ? LocalDate.now() : p.to;
        double bal = 0, debit = 0, credit = 0; LocalDateTime lastMove = null; int moves = 0;
        for (Ledger x : db().ledger) if (x.partyKind == kind && x.partyId == c.id && x.date.isBefore(Ui.end(asOf))) {
            bal += kind == Model.CUSTOMER ? x.debit - x.credit : x.credit - x.debit; debit += x.debit; credit += x.credit; moves++;
            if (lastMove == null || x.date.isAfter(lastMove)) lastMove = x.date; }
        bal = Ui.r(bal);
        String side = Math.abs(bal) < 0.0005 ? "الحساب مسدَّد بالكامل" : kind == Model.CUSTOMER ? (bal > 0 ? "مدين لنا (مستحق على " + PartyUI.noun(kind) + ")" : "دائن (له عندنا)") : (bal > 0 ? "مستحق للمورد (له علينا)" : "مدين لنا (لنا عنده)");
        r.add(PartyUI.noun(kind), c.name);
        if (!c.phone.isBlank()) r.add("الهاتف", c.phone);
        if (!c.address.isBlank()) r.add("العنوان", c.address);
        r.add("الرصيد حتى تاريخ", asOf.toString());
        r.add("إجمالي المدين", Ui.m(debit)); r.add("إجمالي الدائن", Ui.m(credit));
        r.add("الرصيد", new Ui.Bal(kind, bal));
        r.add("الحالة", side);
        r.add("عدد الحركات / آخر حركة", moves + (lastMove == null ? "" : "  /  " + Ui.dt(lastMove)));
        r.add("", "");
        r.add("نص المصادقة", "نرجو التكرم بمراجعة الرصيد أعلاه كما هو في دفاترنا بتاريخ " + asOf + "،");
        r.add("", "والتوقيع بالموافقة، أو بيان أي اختلاف مع ذكر التفاصيل.");
        r.add("", "");
        r.add("[   ] أوافق على الرصيد", "[   ] لا أوافق — الرصيد حسب دفاتري: ..................................");
        r.add("توقيع " + PartyUI.noun(kind), "...........................................   التاريخ: ....................");
        r.add("توقيع وختم " + Store.db.get("storeName", "المحل"), "...........................................");
        r.summary = c.name + "  |  الرصيد حتى " + asOf + ": " + Ui.m(Math.abs(bal)) + "  |  " + side;
        return r;
    }

    static Result invoices(P p, InvType t) {
        Result r = new Result("رقم", "التاريخ", t.customerSide() ? "العميل" : "المورد", "عدد الأصناف", "المجموع", "الخصم", "الضريبة", "الإجمالي", "المدفوع", "المتبقي", "الحالة");
        double tot = 0, paid = 0, rem = 0; int n = 0;
        for (Invoice i : db().invoices) if (i.type == t && in(i.date, p) && (p.party == 0 || i.partyId == p.party)) {
            boolean isCredit = i.total - i.paid > 0.0005;
            if (p.payType.equals("credit") && !isCredit) continue;
            if (p.payType.startsWith("w:")) { long w = Long.parseLong(p.payType.substring(2));
                boolean m1 = i.walletId == w && i.paid - i.paid2 > 0.0005, m2 = i.wallet2 == w && i.paid2 > 0.0005; if (!m1 && !m2) continue; }
            double rm = t.noEffect() ? 0 : Ops.remaining(i);
            r.addInv(i.id, i.no, Ui.dt(i.date), db().partyName(i.partyId), i.lines.size(), i.subtotal, i.discount, i.tax, i.total, i.paid, rm, i.cancelled ? "ملغاة" : "فعّالة");
            if (!i.cancelled) { tot += i.total; paid += i.paid; rem += rm; n++; } }
        r.summary = "عدد الفواتير الفعّالة: " + n + "  |  الإجمالي: " + Ui.m(tot) + (t.noEffect() ? "" : "  |  المدفوع: " + Ui.m(paid) + "  |  المتبقي: " + Ui.m(rem));
        return r;
    }

    static Result purchasesBySupplier(P p) {
        Result r = new Result("المورد", "عدد الفواتير", "المشتريات", "المرتجعات", "الصافي", "الرصيد الحالي");
        Map<Long, double[]> m = new LinkedHashMap<>();
        for (Invoice i : db().invoices) if (!i.cancelled && in(i.date, p) && (i.type == InvType.PURCHASE || i.type == InvType.PURCHASE_RETURN)) {
            double[] a = m.computeIfAbsent(i.partyId, k -> new double[3]); if (i.type == InvType.PURCHASE) { a[0]++; a[1] += i.total; } else a[2] += i.total; }
        Map<Long, Double> b = Ops.allBalances(Model.SUPPLIER); double t = 0;
        for (Map.Entry<Long, double[]> e : m.entrySet()) { double[] a = e.getValue(); r.add(db().partyName(e.getKey()), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[1] - a[2]), b.getOrDefault(e.getKey(), 0.0)); t += a[1] - a[2]; }
        r.summary = "صافي المشتريات: " + Ui.m(t); return r;
    }

    static Result balances(int kind) {
        Result r = new Result("الاسم", "الهاتف", "الرصيد"); Map<Long, Double> b = Ops.allBalances(kind); double t = 0;
        double owe = 0, have = 0;
        for (Party x : db().parties(kind)) { double v = b.getOrDefault(x.id, 0.0); if (Math.abs(v) < 0.0005) continue; Ui.Bal bl = new Ui.Bal(kind, v); r.add(x.name, x.phone, bl); t += v; if (bl.owes) owe += Math.abs(v); else have += Math.abs(v); }
        r.summary = "عليهم (مدين لنا): " + Ui.m(owe) + "  |  لهم (دائن): " + Ui.m(have) + "  |  الصافي: " + Ui.m(Math.abs(t)) + " " + (PartyUI.isDebit(kind, t) ? "لنا" : "علينا"); return r;
    }

    /** أكبر العملاء المدينين: مرتّبون تنازلياً حسب الرصيد المستحق مع نسبة كل عميل من الإجمالي */
    static Result topDebtors(P p) {
        Result r = new Result("#", "العميل", "الهاتف", "الرصيد المستحق", "النسبة من الإجمالي");
        List<Party> ps = new ArrayList<>(); double total = 0;
        for (Party c : db().parties(Model.CUSTOMER)) { double bal = Ops.balance(c); if (bal > 0.0005) { ps.add(c); total += bal; } }
        ps.sort((a, b) -> Double.compare(Ops.balance(b), Ops.balance(a)));
        int i = 0;
        for (Party c : ps) { double bal = Ops.balance(c); r.add(++i, c.name, c.phone, Ui.m(bal), pct(bal, total)); }
        if (!ps.isEmpty()) r.add("", "الإجمالي", "", Ui.m(total), "100%");
        r.summary = ps.isEmpty() ? "لا يوجد عملاء مدينون" : "عدد العملاء المدينين: " + ps.size() + "  |  إجمالي المستحق: " + Ui.m(total);
        return r;
    }

}
