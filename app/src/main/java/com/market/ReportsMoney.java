package com.market;

import com.market.Model.*;
import static com.market.ReportsUI.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** تقارير الصندوق والأرباح والضرائب والمصروفات ومقارنة الفترات */
final class ReportsMoney {
    private ReportsMoney() {}
    static Result cashMovement(P p) {
        Result r = new Result("التاريخ", "النوع", "البيان", "وارد", "صادر", "الرصيد");
        double bal = 0, in = 0, out = 0; List<CashMove> l = new ArrayList<>();
        for (CashMove c : db().cash) if (c.walletId == p.wallet) { if (c.date.isBefore(Ui.start(p.from))) bal += c.amount; else if (c.date.isBefore(Ui.end(p.to))) l.add(c); }
        l.sort(Comparator.comparing(c -> c.date));
        r.add("", "", "رصيد سابق", "", "", Ui.r(bal));
        for (CashMove c : l) { bal += c.amount; if (c.amount > 0) in += c.amount; else out -= c.amount;
            r.addRef(c.ref, c.refId, Ui.dt(c.date), Ops.refTitle(c.ref), c.notes, c.amount > 0 ? c.amount : (Object) "", c.amount < 0 ? -c.amount : (Object) "", Ui.r(bal)); }
        r.summary = db().walletName(p.wallet) + "  |  وارد: " + Ui.m(in) + "  |  صادر: " + Ui.m(out) + "  |  الرصيد: " + Ui.m(bal); return r;
    }


    // ---------------- مقارنة فترتين ----------------
    /** مؤشرات فترة: {صافي المبيعات، التكلفة، الربح، عدد فواتير البيع، المرتجعات، المشتريات، الحبات المباعة} */
    static double[] periodStats(LocalDate from, LocalDate to) {
        P q = new P(); q.from = from; q.to = to; double net = 0, cost = 0, ret = 0, pcs = 0, pur = 0; int n = 0;
        for (Invoice i : db().invoices) {
            if (i.cancelled) continue;
            if (i.type == InvType.PURCHASE) { if (in(i.date, q)) pur += i.total; continue; }
            if (!saleLike(i, q)) continue; int sg = sign(i);
            if (sg > 0) n++; else ret += i.total;
            for (InvLine l : i.lines) { double[] a = amounts(i, l); net += sg * a[0]; cost += sg * a[1]; pcs += sg * a[2]; }
        }
        return new double[]{Ui.r(net), Ui.r(cost), Ui.r(net - cost), n, Ui.r(ret), Ui.r(pur), pcs};
    }
    /** تُقارن الفترة المختارة بفترة سابقة لها نفس الطول تنتهي قبل بدايتها بيوم */
    static Result comparePeriods(P p) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(p.from, p.to) + 1;
        LocalDate pt = p.from.minusDays(1), pf = pt.minusDays(days - 1);
        double[] a = periodStats(p.from, p.to), b = periodStats(pf, pt);
        Result r = new Result("المؤشر", "الفترة الحالية", "الفترة السابقة", "الفرق", "نسبة التغيّر");
        String[] names = {"صافي المبيعات (بدون الضريبة)", "تكلفة المبيعات", "إجمالي الربح", "عدد فواتير البيع", "قيمة المرتجعات", "المشتريات", "الحبات المباعة"};
        for (int k = 0; k < names.length; k++) {
            double d = a[k] - b[k];
            r.add(names[k], k == 3 ? (Object) (int) a[k] : Ui.r(a[k]), k == 3 ? (Object) (int) b[k] : Ui.r(b[k]), Ui.r(d), Math.abs(b[k]) < 0.0005 ? "" : Ui.q(Ui.r(d / Math.abs(b[k]) * 100, 1)) + "%");
        }
        double ma = a[0] == 0 ? 0 : a[2] / a[0] * 100, mb = b[0] == 0 ? 0 : b[2] / b[0] * 100;
        r.add("هامش الربح %", Ui.r(ma, 1), Ui.r(mb, 1), Ui.r(ma - mb, 1), "");
        double aa = a[3] == 0 ? 0 : a[0] / a[3], ab = b[3] == 0 ? 0 : b[0] / b[3];
        r.add("متوسط الفاتورة", Ui.r(aa), Ui.r(ab), Ui.r(aa - ab), Math.abs(ab) < 0.0005 ? "" : Ui.q(Ui.r((aa - ab) / ab * 100, 1)) + "%");
        r.summary = "الحالية: " + p.from + " إلى " + p.to + "  |  السابقة: " + pf + " إلى " + pt + "  |  تغيّر الربح: " + (Math.abs(b[2]) < 0.0005 ? "-" : Ui.q(Ui.r((a[2] - b[2]) / Math.abs(b[2]) * 100, 1)) + "%");
        return r;
    }

    // ---------------- تذبذب أسعار الشراء ----------------
    /** الأصناف التي تغيّر سعر شرائها خلال الفترة (سعر الحبة)، الأكثر تذبذباً أولاً */
    static Result priceFluctuation(P p) {
        Result r = new Result("الصنف", "عدد الشراءات", "أول سعر", "آخر سعر", "أقل سعر", "أعلى سعر", "التغيّر من الأول للآخر", "مدى التذبذب", "آخر مورد");
        Map<Long, List<double[]>> m = new LinkedHashMap<>(); Map<Long, String> sup = new HashMap<>();
        List<Invoice> inv = new ArrayList<>(); for (Invoice i : db().invoices) if (!i.cancelled && i.type == InvType.PURCHASE && in(i.date, p)) inv.add(i);
        inv.sort(Comparator.comparing(i -> i.date));
        for (Invoice i : inv) for (InvLine l : i.lines) { double pc = l.pieces(); if (pc <= 0) continue;
            m.computeIfAbsent(l.productId, k -> new ArrayList<>()).add(new double[]{l.total() / pc}); sup.put(l.productId, db().partyName(i.partyId)); }
        List<Object[]> rows = new ArrayList<>(); List<Double> spread = new ArrayList<>();
        for (Map.Entry<Long, List<double[]>> e : m.entrySet()) {
            List<double[]> v = e.getValue(); if (v.size() < 2) continue;
            double first = v.get(0)[0], last = v.get(v.size() - 1)[0], mn = Double.MAX_VALUE, mx = 0; for (double[] x : v) { mn = Math.min(mn, x[0]); mx = Math.max(mx, x[0]); }
            if (mx - mn < 0.00005) continue; Product pr = db().products.get(e.getKey()); if (pr == null) continue;
            double sp = mn <= 0 ? 0 : (mx - mn) / mn * 100;
            rows.add(new Object[]{pr.name, v.size(), Ui.r(first, 3), Ui.r(last, 3), Ui.r(mn, 3), Ui.r(mx, 3), first <= 0 ? "" : (last >= first ? "+" : "") + Ui.q(Ui.r((last - first) / first * 100, 1)) + "%", Ui.q(Ui.r(sp, 1)) + "%", sup.get(e.getKey())});
            spread.add(sp);
        }
        Integer[] idx = new Integer[rows.size()]; for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(spread.get(y), spread.get(x)));
        for (int i : idx) r.add(rows.get(i));
        r.summary = rows.isEmpty() ? "لا توجد أصناف تغيّر سعر شرائها في هذه الفترة" : "أصناف تغيّر سعر شرائها: " + rows.size() + "  |  رتّبت من الأعلى تذبذباً";
        return r;
    }

    // ---------------- تسوية الصندوق اليومية ----------------
    /** لكل يوم: رصيد أول اليوم + الوارد - الصادر = رصيد آخر اليوم، للمحفظة المختارة */
    static Result dailyCash(P p) {
        Result r = new Result("اليوم", "رصيد أول اليوم", "وارد", "صادر", "رصيد آخر اليوم", "عدد الحركات");
        double bal = 0; TreeMap<LocalDate, double[]> m = new TreeMap<>();
        for (CashMove c : db().cash) if (c.walletId == p.wallet) {
            if (c.date.isBefore(Ui.start(p.from))) bal += c.amount;
            else if (c.date.isBefore(Ui.end(p.to))) { double[] a = m.computeIfAbsent(c.date.toLocalDate(), k -> new double[3]); if (c.amount > 0) a[0] += c.amount; else a[1] -= c.amount; a[2]++; }
        }
        double tin = 0, tout = 0;
        for (Map.Entry<LocalDate, double[]> e : m.entrySet()) { double[] a = e.getValue(); double open = bal; bal += a[0] - a[1]; tin += a[0]; tout += a[1];
            r.add(e.getKey().toString(), Ui.r(open), Ui.r(a[0]), Ui.r(a[1]), Ui.r(bal), (int) a[2]); }
        r.summary = db().walletName(p.wallet) + "  |  وارد: " + Ui.m(tin) + "  |  صادر: " + Ui.m(tout) + "  |  الرصيد الأخير: " + Ui.m(bal);
        return r;
    }

    static Result walletBalances(P p) {
        Result r = new Result("المحفظة", "النوع", "إجمالي الوارد", "إجمالي الصادر", "الرصيد"); double t = 0;
        for (Wallet w : db().wallets) { double in = 0, out = 0; for (CashMove c : db().cash) if (c.walletId == w.id) { if (c.amount > 0) in += c.amount; else out -= c.amount; }
            r.add(w.name, w.type, Ui.r(in), Ui.r(out), Ui.r(in - out)); t += in - out; }
        r.summary = "إجمالي الأرصدة: " + Ui.m(t); return r;
    }

    static Result daily(P p) {
        Result r = new Result("اليوم", "المبيعات", "مرتجع مبيعات", "المشتريات", "مرتجع مشتريات", "المصروفات", "المقبوضات", "المدفوعات", "صافي حركة النقد");
        TreeMap<LocalDate, double[]> m = new TreeMap<>();
        for (Invoice i : db().invoices) if (!i.cancelled && in(i.date, p) && !i.type.noEffect()) {
            double[] a = m.computeIfAbsent(i.date.toLocalDate(), k -> new double[7]); a[i.type.ordinal()] += i.total; }
        for (Expense e : db().expenses) if (!e.cancelled && in(e.date, p)) m.computeIfAbsent(e.date.toLocalDate(), k -> new double[7])[4] += e.amount;
        for (Voucher v : db().vouchers) if (!v.cancelled && in(v.date, p)) m.computeIfAbsent(v.date.toLocalDate(), k -> new double[7])[v.in ? 5 : 6] += v.amount;
        Map<LocalDate, Double> net = new HashMap<>();
        for (CashMove c : db().cash) if (in(c.date, p)) net.merge(c.date.toLocalDate(), c.amount, Double::sum);
        double[] tot = new double[8];
        for (Map.Entry<LocalDate, double[]> e : m.entrySet()) { double[] a = e.getValue(); double n = net.getOrDefault(e.getKey(), 0.0);
            // ترتيب InvType: SALE, PURCHASE, SALE_RETURN, PURCHASE_RETURN
            r.add(e.getKey().toString(), Ui.r(a[0]), Ui.r(a[2]), Ui.r(a[1]), Ui.r(a[3]), Ui.r(a[4]), Ui.r(a[5]), Ui.r(a[6]), Ui.r(n));
            tot[0] += a[0]; tot[1] += a[2]; tot[2] += a[1]; tot[3] += a[3]; tot[4] += a[4]; tot[5] += a[5]; tot[6] += a[6]; tot[7] += n; }
        r.add("الإجمالي", Ui.r(tot[0]), Ui.r(tot[1]), Ui.r(tot[2]), Ui.r(tot[3]), Ui.r(tot[4]), Ui.r(tot[5]), Ui.r(tot[6]), Ui.r(tot[7]));
        r.summary = "صافي المبيعات: " + Ui.m(tot[0] - tot[1]); return r;
    }

    static Result peakHours(P p) {
        java.time.DayOfWeek[] days = {java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY};
        String[] dn = {"السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة"};
        double[][] amt = new double[24][7]; int[][] cnt = new int[24][7]; double total = 0; int n = 0;
        for (Invoice i : db().invoices) if (i.type == InvType.SALE && !i.cancelled && in(i.date, p)) { int h = i.date.getHour(), d = 0;
            for (int k = 0; k < 7; k++) if (days[k] == i.date.getDayOfWeek()) d = k;
            amt[h][d] += i.total; cnt[h][d]++; total += i.total; n++; }
        String[] cols = new String[11]; cols[0] = "الساعة"; for (int k = 0; k < 7; k++) cols[1 + k] = dn[k]; cols[8] = "عدد الفواتير"; cols[9] = "المبيعات"; cols[10] = "النسبة";
        Result r = new Result(cols); double best = -1; int bh = -1; double[] dayTot = new double[7];
        for (int h = 0; h < 24; h++) { double s = 0; int c = 0; for (int k = 0; k < 7; k++) { s += amt[h][k]; c += cnt[h][k]; dayTot[k] += amt[h][k]; }
            if (c == 0) continue; if (s > best) { best = s; bh = h; }
            Object[] row = new Object[11]; row[0] = String.format("%02d:00 - %02d:00", h, (h + 1) % 24);
            for (int k = 0; k < 7; k++) row[1 + k] = cnt[h][k] == 0 ? "" : Ui.r(amt[h][k], 3);
            row[8] = c; row[9] = Ui.r(s, 3); row[10] = pct(s, total); r.add(row); }
        int bd = 0; for (int k = 1; k < 7; k++) if (dayTot[k] > dayTot[bd]) bd = k;
        r.summary = n == 0 ? "لا توجد مبيعات في الفترة" : "عدد الفواتير: " + n + "  |  أكثر ساعة بيعاً: " + String.format("%02d:00", bh) + " (" + Ui.m(best) + ")  |  أكثر يوم: " + dn[bd] + " (" + Ui.m(dayTot[bd]) + ")  |  متوسط الفاتورة: " + Ui.m(total / n);
        return r;
    }

    static Result profit(P p) {
        Result r = new Result("البند", "المبلغ");
        double sales = 0, salesTax = 0, cost = 0, ret = 0, retTax = 0, retCost = 0, disc = 0;
        for (Invoice i : db().invoices) if (!i.cancelled && in(i.date, p)) {
            if (i.type == InvType.SALE) { sales += i.total; salesTax += i.tax; cost += i.costTotal(); disc += i.discount; }
            else if (i.type == InvType.SALE_RETURN) { ret += i.total; retTax += i.tax; retCost += i.costTotal(); }
        }
        double exp = 0; for (Expense e : db().expenses) if (!e.cancelled && in(e.date, p)) exp += e.amount;
        double dmg = 0; for (Damage d : db().damages) if (!d.cancelled && in(d.date, p)) dmg += d.qty * d.cost;
        double netSales = sales - salesTax - (ret - retTax), cogs = cost - retCost, gross = netSales - cogs, net = gross - exp - dmg;
        r.add("إجمالي المبيعات (شامل الضريبة)", Ui.r(sales)); r.add("الخصومات الممنوحة", Ui.r(disc)); r.add("مرتجعات المبيعات", Ui.r(ret));
        r.add("الضريبة المحصلة (صافي)", Ui.r(salesTax - retTax)); r.add("صافي المبيعات بدون ضريبة", Ui.r(netSales));
        r.add("تكلفة البضاعة المباعة", Ui.r(cogs)); r.add("مجمل الربح", Ui.r(gross));
        r.add("المصروفات", Ui.r(exp)); r.add("خسائر التالف", Ui.r(dmg)); r.add("صافي الربح / الخسارة", Ui.r(net));
        r.summary = (net >= 0 ? "صافي ربح: " : "صافي خسارة: ") + Ui.m(Math.abs(net)) + (netSales > 0 ? "  |  هامش مجمل الربح: " + Ui.q(Ui.r(gross / netSales * 100, 1)) + "%" : "");
        return r;
    }

    /** ضريبة المبيعات (مخرجات) وضريبة المشتريات (مدخلات) لكل صنف خلال الفترة */
    static Result taxByItem(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "مبيعات (بدون ضريبة)", "ضريبة المبيعات", "مشتريات (بدون ضريبة)", "ضريبة المشتريات", "الصافي للدائرة");
        Map<Long, double[]> m = new HashMap<>();
        for (Invoice i : db().invoices) { if (i.cancelled || !in(i.date, p)) continue;
            int sg = i.type == InvType.SALE ? 1 : i.type == InvType.SALE_RETURN ? -1 : 0, pg = i.type == InvType.PURCHASE ? 1 : i.type == InvType.PURCHASE_RETURN ? -1 : 0;
            if (sg == 0 && pg == 0) continue;
            for (InvLine l : i.lines) { Product x = db().products.get(l.productId); if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
                double[] am = amounts(i, l), a = m.computeIfAbsent(l.productId, k -> new double[4]); double tx = am[3] - am[0];
                if (sg != 0) { a[0] += sg * am[0]; a[1] += sg * tx; } else { a[2] += pg * am[0]; a[3] += pg * tx; } } }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(Math.abs(y.getValue()[1]) + Math.abs(y.getValue()[3]), Math.abs(x.getValue()[1]) + Math.abs(x.getValue()[3])));
        double[] t = new double[4];
        for (Map.Entry<Long, double[]> e : es) { Product x = db().products.get(e.getKey()); double[] a = e.getValue();
            r.add(x == null ? "" : x.barcode, x == null ? "صنف محذوف" : x.name, x == null ? "" : db().categoryName(x.categoryId), Ui.r(a[0]), Ui.r(a[1]), Ui.r(a[2]), Ui.r(a[3]), Ui.r(a[1] - a[3]));
            for (int k = 0; k < 4; k++) t[k] += a[k]; }
        if (!es.isEmpty()) r.add("", "الإجمالي", "", Ui.r(t[0]), Ui.r(t[1]), Ui.r(t[2]), Ui.r(t[3]), Ui.r(t[1] - t[3]));
        r.summary = es.isEmpty() ? "لا توجد حركات في هذه الفترة" : "ضريبة المخرجات: " + Ui.m(t[1]) + "  |  ضريبة المدخلات: " + Ui.m(t[3]) + "  |  المستحق للدائرة: " + Ui.m(t[1] - t[3]);
        return r;
    }

    static Result taxReport(P p) {
        Result r = new Result("النوع", "عدد الفواتير", "الإجمالي", "الضريبة");
        double[] tax = new double[4], tot = new double[4]; int[] n = new int[4];
        for (Invoice i : db().invoices) if (!i.cancelled && in(i.date, p) && !i.type.noEffect()) { int k = i.type.ordinal(); tax[k] += i.tax; tot[k] += i.total; n[k]++; }
        for (InvType t : new InvType[]{InvType.SALE, InvType.SALE_RETURN, InvType.PURCHASE, InvType.PURCHASE_RETURN}) r.add(t.title, n[t.ordinal()], Ui.r(tot[t.ordinal()]), Ui.r(tax[t.ordinal()]));
        double out = tax[0] - tax[2], inp = tax[1] - tax[3];
        r.summary = "ضريبة المخرجات: " + Ui.m(out) + "  |  ضريبة المدخلات: " + Ui.m(inp) + "  |  المستحق للدائرة: " + Ui.m(out - inp); return r;
    }

    static Result expenses(P p) {
        Result r = new Result("رقم", "التاريخ", "البند", "المبلغ", "المحفظة", "البيان"); double s = 0;
        for (Expense e : db().expenses) if (!e.cancelled && in(e.date, p)) { ExpenseCat c = db().expenseCat(e.catId);
            r.add(e.id, Ui.dt(e.date), c == null ? "" : c.name, e.amount, db().walletName(e.walletId), e.notes); s += e.amount; }
        r.summary = "إجمالي المصروفات: " + Ui.m(s); return r;
    }

    static Result expensesByCat(P p) {
        Result r = new Result("البند", "عدد العمليات", "المبلغ", "النسبة");
        Map<Long, double[]> m = new LinkedHashMap<>(); double s = 0;
        for (Expense e : db().expenses) if (!e.cancelled && in(e.date, p)) { double[] a = m.computeIfAbsent(e.catId, k -> new double[2]); a[0]++; a[1] += e.amount; s += e.amount; }
        for (Map.Entry<Long, double[]> e : m.entrySet()) { ExpenseCat c = db().expenseCat(e.getKey()); r.add(c == null ? "" : c.name, (long) e.getValue()[0], Ui.r(e.getValue()[1]), s == 0 ? "" : Ui.q(Ui.r(e.getValue()[1] / s * 100, 1)) + "%"); }
        r.summary = "الإجمالي: " + Ui.m(s); return r;
    }

}
