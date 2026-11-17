package com.market;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** واجهة عامة لمحرك تقارير البرنامج (نفس تقارير نسخة الكمبيوتر) لاستخدامها من شاشات الهاتف */
public final class ReportApi {
    private ReportApi() {}

    public static final class Def {
        public final int index; public final String group, name; public final int party; public final boolean partyRequired, product, wallet, dates, details,
                category, payType, nos, avail, zakat, wh, account, asOf;
        Def(int i, ReportsUI.Rep r) { index = i; group = r.group; name = r.name; party = r.party; partyRequired = r.partyRequired; product = r.product; wallet = r.wallet;
            dates = r.dates; details = r.details; category = r.category; payType = r.payType; nos = r.nos; avail = r.avail; zakat = r.zakat; wh = r.wh; account = r.account; asOf = r.asOf; }
    }
    public static final class Params {
        public LocalDate from = LocalDate.now(), to = LocalDate.now(); public long party, product, wallet, category, noFrom, noTo; public boolean details;
        public String payType = ""; public int avail; public long wh = -1; public String account = ""; public boolean atSelling = true, includeRec = true, solar; public double goldPrice;
    }
    public static final class Table {
        public String title = "", summary = ""; public String[] cols = new String[0]; public final List<String[]> rows = new ArrayList<>(); public final List<Long> inv = new ArrayList<>();
    }

    public static List<Def> defs() { List<Def> l = new ArrayList<>(); for (int i = 0; i < ReportsUI.REPS.size(); i++) l.add(new Def(i, ReportsUI.REPS.get(i))); return l; }

    public static Table run(int index, Params a) {
        ReportsUI.Rep rep = ReportsUI.REPS.get(index);
        ReportsUI.P p = new ReportsUI.P();
        p.from = a.from; p.to = a.to; p.party = a.party; p.product = a.product == 0 ? null : Store.db.products.get(a.product); p.wallet = a.wallet;
        p.details = a.details; p.category = a.category; p.payType = a.payType == null ? "" : a.payType; p.noFrom = a.noFrom; p.noTo = a.noTo == 0 ? a.noFrom : a.noTo;
        p.avail = a.avail; p.wh = a.wh; p.account = a.account == null ? "" : a.account; p.atSelling = a.atSelling; p.includeRec = a.includeRec; p.solar = a.solar; p.goldPrice = a.goldPrice;
        if (rep.party >= 0 && rep.partyRequired && p.party == 0) throw new Ops.OpException("اختر " + PartyUI.noun(rep.party));
        if (rep.product && p.product == null) throw new Ops.OpException("اختر صنفاً");
        if (rep.nos && p.noFrom <= 0) throw new Ops.OpException("أدخل رقم الفاتورة");
        ReportsUI.Result r = rep.fn.apply(p);
        Table t = new Table(); t.title = rep.name; t.summary = r.summary == null ? "" : r.summary; t.cols = r.cols;
        for (int i = 0; i < r.rows.size(); i++) {
            Object[] row = r.rows.get(i); String[] s = new String[row.length];
            for (int c = 0; c < row.length; c++) s[c] = cell(row[c]);
            t.rows.add(s); t.inv.add(i < r.inv.size() ? r.inv.get(i) : null);
        }
        return t;
    }
    static String cell(Object o) {
        if (o == null) return "";
        if (o instanceof Double || o instanceof Float) return Ui.m(((Number) o).doubleValue());
        return o.toString();
    }
    /** هل في البرنامج أكثر من مستودع؟ */
    public static boolean multiWh() { return Store.db.multiWh(); }
}
