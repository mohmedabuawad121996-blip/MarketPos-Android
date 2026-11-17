package com.market;

import com.market.Model.*;
import static com.market.ReportsUI.*;
import java.time.*;
import java.util.*;
import java.util.List;

/** تقارير المخزون (الجرد والانتهاء والمستودعات والتالف) وتقارير الأصناف والمبيعات المساندة (الأكثر مبيعاً، الراكدة، الكاشير، الشهري، الخصومات، أعمار الذمم) */
final class ReportsStock {
    private ReportsStock() {}
    static Result inventory(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية", "التكلفة", "قيمة بالتكلفة", "سعر البيع", "قيمة بسعر البيع", "الربح المتوقع");
        double vc = 0, vp = 0, tq = 0;
        for (Product x : db().products.values()) { if (!x.active) continue;
            double q = p.wh < 0 ? x.qty : db().whQty(x.id, p.wh);
            if (p.avail == 1 && q <= 0 || p.avail == 2 && q > 0) continue;
            if (p.category != 0 && x.categoryId != p.category) continue;
            double a = Ui.r(q * x.cost), b = Ui.r(q * x.price);
            r.add(x.barcode, x.name, db().categoryName(x.categoryId), Ui.q(q), x.cost, a, x.price, b, Ui.r(b - a)); vc += a; vp += b; tq += q; }
        int items = r.rows.size();
        // سطر الإجمالي: يظهر في الشاشة ويُطبع في آخر التقرير (مجموع التكلفة والبيع والربح المتوقع)
        if (items > 0) r.add("", "الإجمالي (" + items + " صنف)", "", Ui.q(tq), "", Ui.r(vc), "", Ui.r(vp), Ui.r(vp - vc));
        r.summary = (p.wh >= 0 ? db().whName(p.wh) + "  |  " : "") + (p.avail == 1 ? "الأصناف المتوفرة: " : p.avail == 2 ? "الأصناف غير المتوفرة: " : "عدد الأصناف: ") + items + "  |  قيمة المخزون بالتكلفة: " + Ui.m(vc) + "  |  بسعر البيع: " + Ui.m(vp) + "  |  الربح المتوقع: " + Ui.m(vp - vc);
        return r;
    }

    /** جرد مجمّع: سطر لكل تصنيف (عدد الأصناف، الكمية، القيمة بالتكلفة وبسعر البيع) مع الإجمالي */
    static Result inventoryByCategory(P p) {
        Result r = new Result("التصنيف", "عدد الأصناف", "الكمية", "قيمة بالتكلفة", "قيمة بسعر البيع", "الربح المتوقع", "نسبة من المخزون");
        Map<Long, double[]> by = new LinkedHashMap<>(); // count, qty, cost, sale
        for (Product x : db().products.values()) { if (!x.active) continue;
            double q = p.wh < 0 ? x.qty : db().whQty(x.id, p.wh);
            if (p.avail == 1 && q <= 0 || p.avail == 2 && q > 0) continue;
            double[] a = by.computeIfAbsent(x.categoryId, k -> new double[4]); a[0]++; a[1] += q; a[2] += q * x.cost; a[3] += q * x.price; }
        double tc = 0, ts = 0, tq = 0; int tn = 0;
        for (double[] a : by.values()) { tc += a[2]; ts += a[3]; tq += a[1]; tn += (int) a[0]; }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(by.entrySet());
        es.sort((x, y) -> Double.compare(y.getValue()[2], x.getValue()[2])); // الأعلى قيمة أولاً
        for (Map.Entry<Long, double[]> e : es) { double[] a = e.getValue(); String nm = db().categoryName(e.getKey());
            r.add(nm == null || nm.isEmpty() ? "بدون تصنيف" : nm, (long) a[0], Ui.q(a[1]), Ui.r(a[2]), Ui.r(a[3]), Ui.r(a[3] - a[2]), tc == 0 ? "0%" : Ui.q(Ui.r(a[2] * 100 / tc, 1)) + "%"); }
        r.add("الإجمالي", (long) tn, Ui.q(tq), Ui.r(tc), Ui.r(ts), Ui.r(ts - tc), "100%");
        r.summary = (p.wh >= 0 ? db().whName(p.wh) + "  |  " : "") + "عدد التصنيفات: " + by.size() + "  |  عدد الأصناف: " + tn + "  |  قيمة المخزون بالتكلفة: " + Ui.m(tc) + "  |  بسعر البيع: " + Ui.m(ts);
        return r;
    }

    /** جرد أصناف تصنيف واحد (يجب اختيار التصنيف) */
    static Result inventoryOfCategory(P p) {
        if (p.category == 0) { Result r = new Result("الباركود", "الصنف", "الكمية", "التكلفة", "قيمة بالتكلفة"); r.summary = "اختر التصنيف من الأعلى ثم اضغط عرض"; return r; }
        Result r = inventory(p);
        r.summary = "التصنيف: " + db().categoryName(p.category) + "  |  " + r.summary;
        return r;
    }

    /** الأصناف ذات تاريخ صلاحية مرتبة من الأقرب انتهاءً، مع الكمية المتبقية من كل دفعة وقيمتها */
    /** الأصناف الأكثر مبيعاً بالكمية خلال الفترة (مرتبة تنازلياً) مع نسبة كل صنف من إجمالي الكمية */
    static Result topSelling(P p) {
        Result r = new Result("#", "الباركود", "الصنف", "التصنيف", "الكمية المباعة", "صافي المبيعات", "الربح", "النسبة من الكمية", "المتوفر حالياً");
        Map<Long, double[]> m = new HashMap<>();
        for (Invoice i : db().invoices) if (saleLike(i, p)) for (InvLine l : i.lines) {
            Product x = db().products.get(l.productId);
            if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
            double[] am = amounts(i, l), a = m.computeIfAbsent(l.productId, k -> new double[3]); int sg = sign(i);
            a[0] += sg * am[2]; a[1] += sg * am[0]; a[2] += sg * am[1];
        }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet());
        es.removeIf(e -> e.getValue()[0] <= 0);
        es.sort((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0]));
        double tq = 0; for (Map.Entry<Long, double[]> e : es) tq += e.getValue()[0];
        int n = 0;
        for (Map.Entry<Long, double[]> e : es) { Product x = db().products.get(e.getKey()); double[] a = e.getValue(); n++;
            r.add(n, x == null ? "" : x.barcode, x == null ? "صنف محذوف" : x.name, x == null ? "" : db().categoryName(x.categoryId), Ui.q(a[0]), Ui.r(a[1]), Ui.r(a[1] - a[2]),
                    pct(a[0], tq), x == null ? "" : Ui.q(x.qty)); }
        r.summary = es.isEmpty() ? "لا توجد مبيعات في هذه الفترة" : "عدد الأصناف المباعة: " + es.size() + "  |  إجمالي الكمية: " + Ui.q(tq);
        return r;
    }

    /** أصناف لها رصيد بالمخزون ولم تُبَع خلال الفترة المحددة (رأس مال واقف) مع تاريخ آخر بيع */
    static Result stagnant(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية", "التكلفة", "قيمة راكدة بالتكلفة", "آخر بيع", "منذ (يوم)");
        Map<Long, LocalDateTime> last = new HashMap<>(); Set<Long> sold = new HashSet<>();
        for (Invoice i : db().invoices) if (!i.cancelled && i.type == InvType.SALE) for (InvLine l : i.lines) {
            last.merge(l.productId, i.date, (a, b) -> a.isAfter(b) ? a : b);
            if (in(i.date, p)) sold.add(l.productId); }
        List<Product> ps = new ArrayList<>();
        for (Product x : db().products.values()) if (x.active && x.qty > 0 && !sold.contains(x.id) && (p.category == 0 || x.categoryId == p.category)) ps.add(x);
        ps.sort((a, b) -> Double.compare(b.qty * b.cost, a.qty * a.cost));
        double tot = 0; LocalDate today = LocalDate.now();
        for (Product x : ps) { double v = Ui.r(x.qty * x.cost); tot += v; LocalDateTime ld = last.get(x.id);
            r.add(x.barcode, x.name, db().categoryName(x.categoryId), Ui.q(x.qty), Ui.r(x.cost), v, ld == null ? "لم يُبع قط" : Ui.dt(ld),
                    ld == null ? "" : (Object) String.valueOf(java.time.temporal.ChronoUnit.DAYS.between(ld.toLocalDate(), today))); }
        if (ps.isEmpty()) r.summary = "لا توجد أصناف راكدة في هذه الفترة";
        else { r.add("", "الإجمالي", "", "", "", Ui.r(tot), "", ""); r.summary = "عدد الأصناف الراكدة: " + ps.size() + "  |  رأس المال الواقف بالتكلفة: " + Ui.m(tot); }
        return r;
    }

    static String pctOf(double a, double b) { return b == 0 ? "" : Ui.r(a * 100 / b) + "%"; }

    /** المبيعات لكل كاشير خلال الفترة: عدد الفواتير والمبيعات والمرتجعات وصافي المبيعات ومتوسط الفاتورة والربح */
    static Result salesByCashier(P p) {
        Result r = new Result("الكاشير", "عدد الفواتير", "المبيعات", "المرتجعات", "الصافي", "متوسط الفاتورة", "الربح", "النسبة من المبيعات");
        Map<String, double[]> m = new TreeMap<>(); double tn = 0;
        for (Invoice i : db().invoices) if (saleLike(i, p)) {
            String u = i.user == null || i.user.isEmpty() ? "(غير محدد)" : i.user; double[] a = m.computeIfAbsent(u, k -> new double[5]); double net = i.total - i.tax;
            if (sign(i) > 0) { a[0]++; a[1] += net; a[4] += net - i.costTotal(); } else { a[2] += net; a[4] -= net - i.costTotal(); } }
        for (double[] a : m.values()) tn += a[1] - a[2];
        double c = 0, s1 = 0, s2 = 0, s3 = 0, pr = 0;
        for (Map.Entry<String, double[]> e : m.entrySet()) { double[] a = e.getValue(); double net = a[1] - a[2];
            r.add(e.getKey(), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(net), a[0] == 0 ? "" : (Object) Ui.r(a[1] / a[0]), Ui.r(a[4]), pctOf(net, tn)); c += a[0]; s1 += a[1]; s2 += a[2]; s3 += net; pr += a[4]; }
        if (!r.rows.isEmpty()) { r.add("الإجمالي", (long) c, Ui.r(s1), Ui.r(s2), Ui.r(s3), c == 0 ? "" : (Object) Ui.r(s1 / c), Ui.r(pr), ""); }
        r.summary = r.rows.isEmpty() ? "لا توجد مبيعات في هذه الفترة" : "عدد الكاشيرية: " + m.size() + "  |  صافي المبيعات: " + Ui.m(s3) + "  |  الربح: " + Ui.m(pr);
        return r;
    }

    /** المبيعات شهراً بشهر: عدد الفواتير والصافي والتكلفة والربح وهامش الربح */
    static Result salesMonthly(P p) {
        Result r = new Result("الشهر", "عدد الفواتير", "المبيعات (بدون ضريبة)", "المرتجعات", "الصافي", "التكلفة", "الربح", "هامش الربح");
        Map<String, double[]> m = new TreeMap<>();
        for (Invoice i : db().invoices) if (saleLike(i, p)) {
            String k = i.date.getYear() + "-" + String.format("%02d", i.date.getMonthValue()); double[] a = m.computeIfAbsent(k, x -> new double[5]); double net = i.total - i.tax;
            if (sign(i) > 0) { a[0]++; a[1] += net; a[3] += i.costTotal(); } else { a[2] += net; a[3] -= i.costTotal(); } }
        double c = 0, s = 0, rt = 0, cs = 0;
        for (Map.Entry<String, double[]> e : m.entrySet()) { double[] a = e.getValue(); double net = a[1] - a[2];
            r.add(e.getKey(), (long) a[0], Ui.r(a[1]), Ui.r(a[2]), Ui.r(net), Ui.r(a[3]), Ui.r(net - a[3]), pct(net - a[3], net)); c += a[0]; s += a[1]; rt += a[2]; cs += a[3]; }
        if (!r.rows.isEmpty()) r.add("الإجمالي", (long) c, Ui.r(s), Ui.r(rt), Ui.r(s - rt), Ui.r(cs), Ui.r(s - rt - cs), pct(s - rt - cs, s - rt));
        r.summary = r.rows.isEmpty() ? "لا توجد مبيعات في هذه الفترة" : "الأشهر: " + m.size() + "  |  صافي المبيعات: " + Ui.m(s - rt) + "  |  الربح: " + Ui.m(s - rt - cs);
        return r;
    }

    /** الفواتير التي مُنح عليها خصم (يدوي أو نقاط) مع نسبة الخصم والكاشير */
    static Result discountsGiven(P p) {
        Result r = new Result("رقم", "التاريخ", "العميل", "المجموع قبل الخصم", "الخصم", "نسبة الخصم", "الصافي", "الكاشير");
        double td = 0, ts = 0;
        for (Invoice i : db().invoices) if (!i.cancelled && i.type == InvType.SALE && in(i.date, p) && i.discount > 0.0005 && (p.party == 0 || i.partyId == p.party)) {
            r.add(i.no, Ui.dt(i.date), db().partyName(i.partyId), Ui.r(i.subtotal), Ui.r(i.discount), pctOf(i.discount, i.subtotal), Ui.r(i.total), i.user == null ? "" : i.user); td += i.discount; ts += i.subtotal; }
        if (!r.rows.isEmpty()) r.add("", "الإجمالي", "", Ui.r(ts), Ui.r(td), pctOf(td, ts), "", "");
        r.summary = r.rows.isEmpty() ? "لا توجد خصومات في هذه الفترة" : "فواتير بخصم: " + (r.rows.size() - 1) + "  |  إجمالي الخصومات: " + Ui.m(td) + "  |  نسبتها من المبيعات: " + pctOf(td, ts);
        return r;
    }

    /** تفصيل كل خصم في فواتير البيع أو الشراء: خصم الصنف (يدوي أو من عرض) وخصم الفاتورة، كل خصم في سطر مستقل */
    static Result discountsDetail(P p, InvType type) {
        boolean sale = type == InvType.SALE;
        Result r = new Result("رقم الفاتورة", "التاريخ", sale ? "العميل" : "المورد", "نوع الخصم", "الصنف", "الكمية", "السعر",
                "المبلغ قبل الخصم", "الخصم", "النسبة", "بعد الخصم", "العرض / البيان", "المستخدم");
        double lineDisc = 0, invDisc = 0, base = 0; int lines = 0, invs = 0; Set<Long> withDisc = new HashSet<>();
        for (Invoice i : db().invoices) {
            if (i.cancelled || i.type != type || !in(i.date, p) || (p.party != 0 && i.partyId != p.party)) continue;
            String party = db().partyName(i.partyId), user = i.user == null ? "" : i.user;
            for (InvLine l : i.lines) {
                if (l.discount <= 0.0005) continue;
                Product x = db().products.get(l.productId); if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
                double before = Ui.r(l.qty * l.price); boolean offer = l.offer != null && !l.offer.isEmpty() && !l.manualDisc;
                r.addInv(i.id, i.no, Ui.dt(i.date), party, offer ? "عرض" : "خصم صنف", l.name, Ui.q(l.qty) + (l.unit == null || l.unit.isEmpty() ? "" : " " + l.unit),
                        Ui.r(l.price), before, Ui.r(l.discount), pctOf(l.discount, before), l.total(), offer ? l.offer : "", user);
                lineDisc += l.discount; base += before; lines++; withDisc.add(i.id);
            }
            if (i.discount > 0.0005 && p.category == 0) {
                r.addInv(i.id, i.no, Ui.dt(i.date), party, "خصم على الفاتورة", "— كل الفاتورة —", "", "", Ui.r(i.subtotal), Ui.r(i.discount), pctOf(i.discount, i.subtotal),
                        Ui.r(i.subtotal - i.discount), i.notes == null ? "" : i.notes, user);
                invDisc += i.discount; base += i.subtotal; invs++; withDisc.add(i.id);
            }
        }
        double all = lineDisc + invDisc;
        if (!r.rows.isEmpty()) {
            r.add("", "", "", "إجمالي خصم الأصناف", "", "", "", "", Ui.r(lineDisc), "", "", "", "");
            r.add("", "", "", "إجمالي خصم الفواتير", "", "", "", "", Ui.r(invDisc), "", "", "", "");
            r.add("", "", "", "إجمالي الخصومات", "", "", "", "", Ui.r(all), "", "", "", "");
        }
        r.summary = r.rows.isEmpty() ? "لا توجد خصومات في " + (sale ? "فواتير البيع" : "فواتير الشراء") + " خلال هذه الفترة"
                : "فواتير فيها خصم: " + withDisc.size() + "  |  خصم الأصناف: " + Ui.m(lineDisc) + " (" + lines + " سطر)  |  خصم الفواتير: " + Ui.m(invDisc) + " (" + invs + ")"
                  + "  |  الإجمالي: " + Ui.m(all) + (sale ? " ممنوحة للزبائن" : " حصلنا عليه من الموردين");
        return r;
    }

    private static Result itemTotals(P p, InvType type, String valHead, boolean avg) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية", valHead, avg ? "متوسط السعر" : "عدد الفواتير");
        Map<Long, double[]> m = new HashMap<>();
        for (Invoice i : db().invoices) if (!i.cancelled && i.type == type && in(i.date, p)) for (InvLine l : i.lines) {
            Product x = db().products.get(l.productId); if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
            double[] a = m.computeIfAbsent(l.productId, k -> new double[3]); a[0] += l.qty * Math.max(1, l.factor); a[1] += l.total(); a[2]++; }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>(m.entrySet()); es.sort((x, y) -> Double.compare(y.getValue()[1], x.getValue()[1]));
        double tq = 0, tv = 0;
        for (Map.Entry<Long, double[]> e : es) { Product x = db().products.get(e.getKey()); double[] a = e.getValue();
            r.add(x == null ? "" : x.barcode, x == null ? "صنف محذوف" : x.name, x == null ? "" : db().categoryName(x.categoryId), Ui.q(a[0]), Ui.r(a[1]), avg ? (a[0] == 0 ? "" : (Object) Ui.r(a[1] / a[0])) : (Object) (long) a[2]); tq += a[0]; tv += a[1]; }
        if (!es.isEmpty()) r.add("", "الإجمالي", "", Ui.q(tq), Ui.r(tv), "");
        r.summary = es.isEmpty() ? "لا توجد حركات في هذه الفترة" : "الأصناف: " + es.size() + "  |  الكمية: " + Ui.q(tq) + "  |  القيمة: " + Ui.m(tv);
        return r;
    }
    static Result purchasesByItem(P p) { return itemTotals(p, InvType.PURCHASE, "إجمالي الشراء", true); }
    static Result returnsByItem(P p) { return itemTotals(p, InvType.SALE_RETURN, "قيمة المرتجع", false); }

    /** أعمار الذمم: كم مستحق لنا على كل عميل ومنذ متى (يُخصَّص الرصيد الحالي على أحدث الفواتير الآجلة أولاً) */
    static Result agingCustomers(P p) {
        Result r = new Result("العميل", "0 - 30 يوم", "31 - 60 يوم", "61 - 90 يوم", "أكثر من 90 يوم / افتتاحي", "الإجمالي المستحق");
        double[] tot = new double[4]; LocalDate today = LocalDate.now(); int n = 0;
        List<Party> ps = new ArrayList<>(db().parties.values()); ps.removeIf(x -> x.kind != Model.CUSTOMER);
        ps.sort(Comparator.comparing(x -> x.name == null ? "" : x.name));
        for (Party c : ps) {
            double bal = Ops.balance(c); if (bal <= 0.0005) continue; double left = bal; double[] b = new double[4];
            List<Invoice> inv = new ArrayList<>(); for (Invoice i : db().invoices) if (!i.cancelled && i.type == InvType.SALE && i.partyId == c.id && i.total - i.paid > 0.0005) inv.add(i);
            inv.sort((x, y) -> y.date.compareTo(x.date));
            for (Invoice i : inv) { if (left <= 0.0005) break; double take = Math.min(left, i.total - i.paid); long d = java.time.temporal.ChronoUnit.DAYS.between(i.date.toLocalDate(), today);
                b[d <= 30 ? 0 : d <= 60 ? 1 : d <= 90 ? 2 : 3] += take; left -= take; }
            b[3] += Math.max(0, left);
            r.add(c.name, Ui.r(b[0]), Ui.r(b[1]), Ui.r(b[2]), Ui.r(b[3]), Ui.r(bal)); for (int k = 0; k < 4; k++) tot[k] += b[k]; n++;
        }
        double all = tot[0] + tot[1] + tot[2] + tot[3];
        if (n > 0) r.add("الإجمالي", Ui.r(tot[0]), Ui.r(tot[1]), Ui.r(tot[2]), Ui.r(tot[3]), Ui.r(all));
        r.summary = n == 0 ? "لا توجد ذمم مستحقة على العملاء" : "عملاء عليهم رصيد: " + n + "  |  إجمالي المستحق: " + Ui.m(all) + "  |  متأخر أكثر من 60 يوم: " + Ui.m(tot[2] + tot[3]);
        return r;
    }

    /** الفواتير الملغاة خلال الفترة (مبيعات ومشتريات ومرتجعات) للرقابة */
    static Result cancelledInvoices(P p) {
        Result r = new Result("النوع", "الرقم", "التاريخ", "الطرف", "الإجمالي", "الكاشير", "ملاحظات");
        double t = 0; int n = 0;
        List<Invoice> l = new ArrayList<>(); for (Invoice i : db().invoices) if (i.cancelled && in(i.date, p)) l.add(i);
        l.sort((a, b) -> b.date.compareTo(a.date));
        for (Invoice i : l) { r.add(i.type.title, i.no, Ui.dt(i.date), db().partyName(i.partyId), Ui.r(i.total), i.user == null ? "" : i.user, i.notes == null ? "" : i.notes); r.inv.set(r.inv.size() - 1, i.id); t += i.total; n++; }
        if (n > 0) r.add("الإجمالي", "", "", "", Ui.r(t), "", "");
        r.summary = n == 0 ? "لا توجد فواتير ملغاة في هذه الفترة" : "عدد الفواتير الملغاة: " + n + "  |  قيمتها: " + Ui.m(t);
        return r;
    }

    /** أصناف بيعت بهامش ربح أقل من 10% أو بخسارة خلال الفترة، مرتبة من الأسوأ */
    static Result lowMargin(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية", "صافي المبيعات", "التكلفة", "الربح", "هامش الربح");
        Map<Long, double[]> m = new HashMap<>();
        for (Invoice i : db().invoices) if (saleLike(i, p)) for (InvLine l : i.lines) {
            Product x = db().products.get(l.productId); if (p.category != 0 && (x == null || x.categoryId != p.category)) continue;
            double[] am = amounts(i, l), a = m.computeIfAbsent(l.productId, k -> new double[3]); int sg = sign(i);
            a[0] += sg * am[2]; a[1] += sg * am[0]; a[2] += sg * am[1]; }
        List<Map.Entry<Long, double[]>> es = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : m.entrySet()) { double[] a = e.getValue(); if (a[1] > 0.0005 && (a[1] - a[2]) / a[1] < 0.10) es.add(e); }
        es.sort((x, y) -> Double.compare((x.getValue()[1] - x.getValue()[2]) / x.getValue()[1], (y.getValue()[1] - y.getValue()[2]) / y.getValue()[1]));
        double loss = 0; int neg = 0;
        for (Map.Entry<Long, double[]> e : es) { Product x = db().products.get(e.getKey()); double[] a = e.getValue(); double pr = a[1] - a[2];
            r.add(x == null ? "" : x.barcode, x == null ? "صنف محذوف" : x.name, x == null ? "" : db().categoryName(x.categoryId), Ui.q(a[0]), Ui.r(a[1]), Ui.r(a[2]), Ui.r(pr), pct(pr, a[1]));
            if (pr < 0) { neg++; loss += pr; } }
        r.summary = es.isEmpty() ? "لا توجد أصناف بربح أقل من 10% في هذه الفترة" : "أصناف هامشها أقل من 10%: " + es.size() + "  |  منها بخسارة: " + neg + (neg > 0 ? " (" + Ui.m(loss) + ")" : "");
        return r;
    }

    /** أعمار ذمم الموردين: ما علينا لكل مورد ومنذ متى (الرصيد الحالي يُوزَّع على أحدث فواتير الشراء غير المسددة أولاً) */
    static Result agingSuppliers(P p) {
        Result r = new Result("المورد", "0 - 30 يوم", "31 - 60 يوم", "61 - 90 يوم", "أكثر من 90 يوم / افتتاحي", "الإجمالي علينا");
        double[] tot = new double[4]; LocalDate today = LocalDate.now(); int n = 0;
        List<Party> ps = new ArrayList<>(db().parties.values()); ps.removeIf(x -> x.kind != Model.SUPPLIER);
        ps.sort(Comparator.comparing(x -> x.name == null ? "" : x.name));
        for (Party c : ps) {
            double bal = Ops.balance(c); if (bal <= 0.0005) continue; double left = bal; double[] b = new double[4];
            List<Invoice> inv = new ArrayList<>(); for (Invoice i : db().invoices) if (!i.cancelled && i.type == InvType.PURCHASE && i.partyId == c.id && i.total - i.paid > 0.0005) inv.add(i);
            inv.sort((x, y) -> y.date.compareTo(x.date));
            for (Invoice i : inv) { if (left <= 0.0005) break; double take = Math.min(left, i.total - i.paid); long d = java.time.temporal.ChronoUnit.DAYS.between(i.date.toLocalDate(), today);
                b[d <= 30 ? 0 : d <= 60 ? 1 : d <= 90 ? 2 : 3] += take; left -= take; }
            b[3] += Math.max(0, left);
            r.add(c.name, Ui.r(b[0]), Ui.r(b[1]), Ui.r(b[2]), Ui.r(b[3]), Ui.r(bal)); for (int k = 0; k < 4; k++) tot[k] += b[k]; n++;
        }
        double all = tot[0] + tot[1] + tot[2] + tot[3];
        if (n > 0) r.add("الإجمالي", Ui.r(tot[0]), Ui.r(tot[1]), Ui.r(tot[2]), Ui.r(tot[3]), Ui.r(all));
        r.summary = n == 0 ? "لا توجد مبالغ مستحقة للموردين" : "موردون لهم رصيد: " + n + "  |  إجمالي المستحق علينا: " + Ui.m(all) + "  |  متأخر أكثر من 60 يوم: " + Ui.m(tot[2] + tot[3]);
        return r;
    }

    static Result expiryReport(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "تاريخ الانتهاء", "الأيام المتبقية", "الكمية المتبقية", "قيمة بالتكلفة", "الحالة");
        int ex = 0, soon = 0; double vex = 0, vsoon = 0, tot = 0; int alert = Expiry.alertDays();
        for (Expiry.Row x : Expiry.open()) {
            if (p.category != 0 && x.p.categoryId != p.category) continue;
            double v = Ui.r(x.left * x.p.cost);
            String st = x.days < 0 ? "منتهي" : x.days <= alert ? "قريب الانتهاء" : "سليم";
            if (x.days < 0) { ex++; vex += v; } else if (x.days <= alert) { soon++; vsoon += v; }
            tot += v;
            r.add(x.p.barcode, x.p.name, db().categoryName(x.p.categoryId), x.b.expiry, x.days < 0 ? "منذ " + (-x.days) + " يوم" : x.days + " يوم", Ui.q(x.left), v, st);
        }
        if (r.rows.isEmpty()) r.summary = "لا توجد أصناف بتاريخ صلاحية في المخزون (يُسجَّل تاريخ الصلاحية عند الشراء)";
        else { r.add("", "الإجمالي", "", "", "", "", Ui.r(tot), "");
            r.summary = "منتهي: " + ex + " (" + Ui.m(vex) + ")  |  قريب الانتهاء خلال " + alert + " يوم: " + soon + " (" + Ui.m(vsoon) + ")  |  إجمالي القيمة: " + Ui.m(tot); }
        return r;
    }

    static Result lowStock(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية الحالية", "حد الطلب", "النقص");
        for (Product x : db().products.values()) if (x.active && x.qty <= x.minQty && (x.minQty > 0 || x.qty < 0))
            r.add(x.barcode, x.name, db().categoryName(x.categoryId), Ui.q(x.qty), Ui.q(x.minQty), Ui.q(x.minQty - x.qty));
        r.summary = "عدد الأصناف: " + r.rows.size(); return r;
    }

    static Result itemMovement(P p) {
        Result r = new Result("التاريخ", "نوع الحركة", "البيان", "وارد", "صادر", "الرصيد", "التكلفة");
        double bal = 0, in = 0, out = 0; List<StockMove> l = new ArrayList<>();
        for (StockMove s : db().stock) if (s.productId == p.product.id && moveIn(s, p)) { if (s.date.isBefore(Ui.start(p.from))) bal += s.qty; else if (s.date.isBefore(Ui.end(p.to))) l.add(s); }
        l.sort(Comparator.comparing(s -> s.date));
        r.add("", "", "رصيد سابق", "", "", Ui.q(bal), "");
        for (StockMove s : l) { bal += s.qty; if (s.qty > 0) in += s.qty; else out -= s.qty;
            r.addRef(s.ref, s.refId, Ui.dt(s.date), Ops.refTitle(s.ref), s.notes, s.qty > 0 ? Ui.q(s.qty) : "", s.qty < 0 ? Ui.q(-s.qty) : "", Ui.q(bal), s.cost); }
        r.summary = p.product.name + "  |  وارد: " + Ui.q(in) + "  |  صادر: " + Ui.q(out) + "  |  الرصيد: " + Ui.q(bal) + "  |  الرصيد الحالي بالنظام: " + Ui.q(p.product.qty);
        return r;
    }

    /** فلتر المستودع: بدون مستودع تُتجاهل حركات التحويل (صافيها صفر) */
    static boolean moveIn(StockMove s, P p) { return p.wh < 0 ? !"TRANSFER".equals(s.ref) : db().whOf(s.warehouseId) == p.wh; }

    static Result warehouses(P p) {
        List<Warehouse> ws = db().warehouses;
        String[] cols = new String[ws.size() + 5]; cols[0] = "الباركود"; cols[1] = "الصنف"; cols[2] = "التصنيف";
        for (int i = 0; i < ws.size(); i++) cols[3 + i] = ws.get(i).name + (ws.get(i).active ? "" : " (موقوف)");
        cols[ws.size() + 3] = "الإجمالي"; cols[ws.size() + 4] = "القيمة بالتكلفة";
        Result r = new Result(cols); double[] tot = new double[ws.size()]; double val = 0;
        for (Product x : db().products.values()) { if (!x.active) continue; if (p.category != 0 && x.categoryId != p.category) continue;
            Object[] row = new Object[cols.length]; row[0] = x.barcode; row[1] = x.name; row[2] = db().categoryName(x.categoryId); boolean any = false;
            for (int i = 0; i < ws.size(); i++) { double q = db().whQty(x.id, ws.get(i).id); tot[i] += q * x.cost; row[3 + i] = Ui.q(q); if (Math.abs(q) > 1e-9) any = true; }
            if (!any && Math.abs(x.qty) < 1e-9) continue;
            row[ws.size() + 3] = Ui.q(x.qty); row[ws.size() + 4] = Ui.r(x.qty * x.cost); val += x.qty * x.cost; r.add(row); }
        StringBuilder b = new StringBuilder("عدد الأصناف: " + r.rows.size());
        for (int i = 0; i < ws.size(); i++) b.append("  |  ").append(ws.get(i).name).append(": ").append(Ui.m(tot[i]));
        r.summary = b.append("  |  الإجمالي: ").append(Ui.m(val)).toString(); return r;
    }

    static Result transfers(P p) {
        Result r = new Result("رقم", "التاريخ", "من", "إلى", "الصنف", "الكمية", "القيمة بالتكلفة", "المستخدم", "الحالة"); double v = 0; int n = 0;
        for (Transfer t : db().transfers) { if (t.date.isBefore(Ui.start(p.from)) || !t.date.isBefore(Ui.end(p.to))) continue; n++;
            for (String it : t.items.split(";")) { if (it.isEmpty()) continue; String[] a = it.split(":"); Product x = db().products.get(Long.parseLong(a[0])); double q = Double.parseDouble(a[1]);
                double c = x == null ? 0 : Ui.r(q * x.cost); if (!t.cancelled) v += c;
                r.add(t.no, Ui.dt(t.date), db().whName(t.fromWh), db().whName(t.toWh), x == null ? "(محذوف)" : x.name, Ui.q(q), c, t.user, t.cancelled ? "ملغى" : "منفّذ"); } }
        r.summary = "عدد التحويلات: " + n + "  |  قيمة البضاعة المحوّلة: " + Ui.m(v); return r;
    }

    static Result stockMovement(P p) {
        Result r = new Result("الباركود", "الصنف", "أول المدة", "مشتريات", "مرتجع مبيعات", "مبيعات", "مرتجع مشتريات", "تالف", "تسويات", "آخر المدة");
        Map<Long, double[]> m = new LinkedHashMap<>();
        for (StockMove s : db().stock) {
            if (!s.date.isBefore(Ui.end(p.to)) || !moveIn(s, p)) continue;
            double[] a = m.computeIfAbsent(s.productId, k -> new double[8]);
            if (s.date.isBefore(Ui.start(p.from))) { a[0] += s.qty; continue; }
            switch (s.ref) { case "PURCHASE": a[1] += s.qty; break; case "SALE_RETURN": a[2] += s.qty; break; case "SALE": a[3] -= s.qty; break;
                case "PURCHASE_RETURN": a[4] -= s.qty; break; case "DAMAGE": a[5] -= s.qty; break; default: a[6] += s.qty; }
        }
        for (Map.Entry<Long, double[]> e : m.entrySet()) { Product x = db().products.get(e.getKey()); if (x == null) continue; double[] a = e.getValue();
            double end = a[0] + a[1] + a[2] - a[3] - a[4] - a[5] + a[6];
            r.add(x.barcode, x.name, Ui.q(a[0]), Ui.q(a[1]), Ui.q(a[2]), Ui.q(a[3]), Ui.q(a[4]), Ui.q(a[5]), Ui.q(a[6]), Ui.q(end)); }
        r.summary = "عدد الأصناف المتحركة: " + r.rows.size(); return r;
    }

    static Result damages(P p) {
        Result r = new Result("رقم", "التاريخ", "الصنف", "الكمية", "التكلفة", "القيمة", "السبب", "الحالة"); double s = 0;
        for (Damage d : db().damages) if (in(d.date, p)) { Product x = db().products.get(d.productId);
            r.add(d.id, Ui.dt(d.date), x == null ? "" : x.name, Ui.q(d.qty), d.cost, Ui.r(d.qty * d.cost), d.reason, d.cancelled ? "ملغى" : "فعّال");
            if (!d.cancelled) s += d.qty * d.cost; }
        r.summary = "إجمالي قيمة التالف: " + Ui.m(s); return r;
    }

    /** أصناف ناقصة البيانات: بدون باركود أو بدون سعر بيع أو بدون تكلفة — تُصحَّح قبل الاعتماد على التقارير */
    static Result missingData(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "الكمية", "التكلفة", "سعر البيع", "الناقص");
        int n = 0;
        for (Product x : db().products.values()) {
            if (!x.active) continue;
            List<String> miss = new ArrayList<>();
            if (x.barcode == null || x.barcode.trim().isEmpty()) miss.add("باركود");
            if (x.price <= 0.0005) miss.add("سعر بيع");
            if (x.cost <= 0.0005) miss.add("تكلفة");
            if (miss.isEmpty()) continue;
            r.add(x.barcode, x.name, db().categoryName(x.categoryId), Ui.q(x.qty), x.cost <= 0.0005 ? "—" : Ui.m(x.cost), x.price <= 0.0005 ? "—" : Ui.m(x.price), String.join(" + ", miss)); n++;
        }
        r.summary = n == 0 ? "كل الأصناف بياناتها مكتملة" : "أصناف ناقصة البيانات: " + n;
        return r;
    }

    /** أصناف تحتاج إعادة تسعير: تُباع بخسارة أو بهامش ربح منخفض مقابل تكلفتها الحالية */
    static Result needsRepricing(P p) {
        Result r = new Result("الباركود", "الصنف", "التصنيف", "التكلفة", "سعر البيع", "هامش الربح", "الحالة");
        double thr = 7; // الحد الأدنى المقبول لهامش الربح %
        List<Object[]> under = new ArrayList<>();
        for (Product x : db().products.values()) {
            if (!x.active || x.price <= 0.0005 || x.cost <= 0.0005) continue;
            double margin = (x.price - x.cost) / x.price * 100;
            if (margin >= thr) continue;
            String state = x.price < x.cost ? "بيع بخسارة" : margin < 0.0005 ? "بلا ربح" : "هامش منخفض";
            under.add(new Object[]{margin, x.barcode, x.name, db().categoryName(x.categoryId), Ui.m(x.cost), Ui.m(x.price), Ui.q(Ui.r(margin, 1)) + "%", state});
        }
        under.sort(Comparator.comparingDouble(a -> (double) a[0]));
        for (Object[] a : under) r.add(a[1], a[2], a[3], a[4], a[5], a[6], a[7]);
        r.summary = under.isEmpty() ? "لا توجد أصناف بهامش أقل من " + (int) thr + "%" : "أصناف تحتاج إعادة تسعير (هامش < " + (int) thr + "%): " + under.size();
        return r;
    }

}
