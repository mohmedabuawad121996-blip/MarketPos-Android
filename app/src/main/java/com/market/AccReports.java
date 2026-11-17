package com.market;

import java.util.*;

/** تقارير المحاسبة: اليومية، الأستاذ العام، ميزان المراجعة، قائمة الدخل، الميزانية العمومية */
final class AccReports {
    private AccReports() {}

    static String dt(Accounting.Entry e) { return Ui.dt(e.date); }

    static ReportsUI.Result journal(ReportsUI.P p) {
        ReportsUI.Result r = new ReportsUI.Result("رقم القيد", "التاريخ", "البيان", "الحساب", "مدين", "دائن");
        Map<String, Accounting.Acc> m = Accounting.chartMap(); double d = 0, c = 0; int n = 0, seq = 0;
        for (Accounting.Entry e : Accounting.all()) { seq++;
            if (e.date.isBefore(Ui.start(p.from)) || !e.date.isBefore(Ui.end(p.to))) continue; n++; boolean first = true;
            for (Accounting.Line l : e.lines) {
                r.addRef(e.ref, e.refId, first ? seq : "", first ? dt(e) : "", first ? e.desc : (l.memo.isEmpty() ? "" : l.memo),
                        (l.cr > 0 ? "      " : "") + Accounting.accName(m, l.acc), l.dr > 0 ? Ui.r(l.dr, 3) : "", l.cr > 0 ? Ui.r(l.cr, 3) : "");
                first = false; d += l.dr; c += l.cr; }
        }
        r.summary = "عدد القيود: " + n + "  |  مجموع المدين: " + Ui.m(d) + "  |  مجموع الدائن: " + Ui.m(c);
        return r;
    }

    static ReportsUI.Result ledger(ReportsUI.P p) {
        Map<String, Accounting.Acc> m = Accounting.chartMap(); Accounting.Acc a = m.get(p.account);
        ReportsUI.Result r = new ReportsUI.Result("التاريخ", "البيان", "مدين", "دائن", "الرصيد", "طبيعة الرصيد");
        if (a == null) { r.summary = "اختر الحساب"; return r; }
        double bal = 0, d = 0, c = 0; List<Object[]> rows = new ArrayList<>();
        List<Accounting.Entry> es = Accounting.all();
        for (Accounting.Entry e : es) if (e.date.isBefore(Ui.start(p.from))) for (Accounting.Line l : e.lines) if (l.acc.equals(a.key)) bal += l.dr - l.cr;
        r.add("", "رصيد سابق", "", "", Ui.r(Math.abs(bal), 3), side(bal));
        for (Accounting.Entry e : es) { if (e.date.isBefore(Ui.start(p.from)) || !e.date.isBefore(Ui.end(p.to))) continue;
            for (Accounting.Line l : e.lines) if (l.acc.equals(a.key)) { bal += l.dr - l.cr; d += l.dr; c += l.cr;
                r.addRef(e.ref, e.refId, dt(e), e.desc + (l.memo.isEmpty() ? "" : " (" + l.memo + ")"), l.dr > 0 ? Ui.r(l.dr, 3) : "", l.cr > 0 ? Ui.r(l.cr, 3) : "", Ui.r(Math.abs(bal), 3), side(bal)); } }
        r.summary = a + "  |  حركة مدين: " + Ui.m(d) + "  |  حركة دائن: " + Ui.m(c) + "  |  الرصيد: " + Ui.m(Math.abs(bal)) + " " + side(bal);
        return r;
    }
    static String side(double b) { return Math.abs(b) < 0.0005 ? "" : b > 0 ? "مدين" : "دائن"; }

    static ReportsUI.Result trial(ReportsUI.P p) {
        ReportsUI.Result r = new ReportsUI.Result("الرمز", "الحساب", "رصيد سابق مدين", "رصيد سابق دائن", "حركة مدين", "حركة دائن", "الرصيد مدين", "الرصيد دائن");
        List<Accounting.Entry> es = Accounting.all();
        Map<String, double[]> before = Accounting.totals(es, null, p.from.minusDays(1)), per = Accounting.totals(es, p.from, p.to);
        double[] tot = new double[6];
        for (Accounting.Acc a : Accounting.chart()) {
            double[] b = before.getOrDefault(a.key, new double[2]), x = per.getOrDefault(a.key, new double[2]);
            double ob = b[0] - b[1], cb = ob + x[0] - x[1];
            if (Math.abs(b[0]) + Math.abs(b[1]) + Math.abs(x[0]) + Math.abs(x[1]) < 0.0005) continue;
            double[] v = {Math.max(0, ob), Math.max(0, -ob), x[0], x[1], Math.max(0, cb), Math.max(0, -cb)};
            for (int i = 0; i < 6; i++) tot[i] += v[i];
            r.add(a.code, a.name, z(v[0]), z(v[1]), z(v[2]), z(v[3]), z(v[4]), z(v[5]));
        }
        r.add("", "الإجمالي", Ui.r(tot[0], 3), Ui.r(tot[1], 3), Ui.r(tot[2], 3), Ui.r(tot[3], 3), Ui.r(tot[4], 3), Ui.r(tot[5], 3));
        double diff = Ui.r(tot[4] - tot[5], 3);
        r.summary = (Math.abs(diff) < 0.001 ? "الميزان متوازن" : "الميزان غير متوازن — الفرق " + Ui.m(diff)) + "  |  مجموع الأرصدة المدينة: " + Ui.m(tot[4]) + "  |  الدائنة: " + Ui.m(tot[5]);
        return r;
    }
    static Object z(double v) { return Math.abs(v) < 0.0005 ? "" : Ui.r(v, 3); }

    static ReportsUI.Result income(ReportsUI.P p) {
        ReportsUI.Result r = new ReportsUI.Result("البند", "المبلغ", "الإجمالي");
        Map<String, double[]> t = Accounting.totals(Accounting.all(), p.from, p.to);
        double rev = 0, exp = 0, cogs = 0;
        r.add("الإيرادات", "", "");
        for (Accounting.Acc a : Accounting.chart()) if (a.type == Accounting.REVENUE) { double b = Accounting.bal(a, t.get(a.key)); if (Math.abs(b) < 0.0005) continue; rev += b; r.add("   " + a.name, Ui.r(b, 3), ""); }
        r.add("صافي الإيرادات", "", Ui.r(rev, 3));
        Accounting.Acc cg = Accounting.chartMap().get(Accounting.COGS); cogs = Accounting.bal(cg, t.get(cg.key));
        r.add("   " + cg.name, Ui.r(cogs, 3), ""); r.add("مجمل الربح", "", Ui.r(rev - cogs, 3));
        r.add("المصروفات", "", "");
        for (Accounting.Acc a : Accounting.chart()) if (a.type == Accounting.EXPENSE && !a.key.equals(Accounting.COGS)) { double b = Accounting.bal(a, t.get(a.key)); if (Math.abs(b) < 0.0005) continue; exp += b; r.add("   " + a.name, Ui.r(b, 3), ""); }
        r.add("إجمالي المصروفات", "", Ui.r(exp, 3));
        double net = rev - cogs - exp;
        r.add(net >= 0 ? "صافي الربح" : "صافي الخسارة", "", Ui.r(Math.abs(net), 3));
        r.summary = (net >= 0 ? "صافي ربح الفترة: " : "صافي خسارة الفترة: ") + Ui.m(Math.abs(net)) + (rev > 0 ? "  |  هامش مجمل الربح: " + Ui.q(Ui.r((rev - cogs) / rev * 100, 1)) + "%" : "");
        return r;
    }

    static ReportsUI.Result balanceSheet(ReportsUI.P p) {
        ReportsUI.Result r = new ReportsUI.Result("البند", "المبلغ", "الإجمالي");
        Map<String, double[]> t = Accounting.totals(Accounting.all(), null, p.to);
        List<Accounting.Acc> ch = Accounting.chart(); double[] s = new double[5];
        for (Accounting.Acc a : ch) s[a.type] += Accounting.bal(a, t.get(a.key));
        double ni = s[Accounting.REVENUE] - s[Accounting.EXPENSE];
        section(r, "الأصول", Accounting.ASSET, ch, t); r.add("إجمالي الأصول", "", Ui.r(s[0], 3));
        section(r, "الخصوم", Accounting.LIABILITY, ch, t); r.add("إجمالي الخصوم", "", Ui.r(s[1], 3));
        section(r, "حقوق الملكية", Accounting.EQUITY, ch, t);
        r.add("   صافي الربح المتراكم (الإيرادات - المصروفات)", Ui.r(ni, 3), "");
        r.add("إجمالي حقوق الملكية", "", Ui.r(s[2] + ni, 3));
        r.add("إجمالي الخصوم وحقوق الملكية", "", Ui.r(s[1] + s[2] + ni, 3));
        double inv = 0; for (Model.Product x : Store.db.products.values()) if (x.qty > 0) inv += x.qty * x.cost;
        double diff = Ui.r(s[0] - (s[1] + s[2] + ni), 3);
        r.summary = "حتى " + p.to + "  |  " + (Math.abs(diff) < 0.001 ? "الميزانية متوازنة" : "غير متوازنة — الفرق " + Ui.m(diff))
                + "  |  قيمة المخزون الفعلية بالتكلفة الحالية: " + Ui.m(inv);
        return r;
    }
    static void section(ReportsUI.Result r, String title, int type, List<Accounting.Acc> ch, Map<String, double[]> t) {
        r.add(title, "", "");
        for (Accounting.Acc a : ch) if (a.type == type) { double b = Accounting.bal(a, t.get(a.key)); if (Math.abs(b) < 0.0005) continue; r.add("   " + a.name, Ui.r(b, 3), ""); }
    }
}
