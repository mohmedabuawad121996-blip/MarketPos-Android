package com.market;

import com.market.Model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * المحاسبة بالقيد المزدوج: دليل الحسابات، القيود اليومية المولّدة تلقائياً من حركات البرنامج
 * (فواتير، سندات، مصروفات، صندوق، مخزون، أرصدة افتتاحية) + القيود اليدوية.
 * القيود لا تُخزَّن بل تُشتق في كل مرة من الحركات، فتبقى مطابقة دائماً للأرصدة.
 */
public final class Accounting {
    private Accounting() {}
    static Store db() { return Store.db; }

    public static final int ASSET = Account.ASSET, LIABILITY = Account.LIABILITY, EQUITY = Account.EQUITY, REVENUE = Account.REVENUE, EXPENSE = Account.EXPENSE;
    public static final String[] TYPES = {"أصول", "خصوم", "حقوق ملكية", "إيرادات", "مصروفات"};

    public static final class Acc {
        public final String key, code, name; public final int type; public final boolean system;
        Acc(String key, String code, String name, int type, boolean system) { this.key = key; this.code = code; this.name = name; this.type = type; this.system = system; }
        /** طبيعة الحساب مدينة؟ */
        public boolean debitNature() { return type == ASSET || type == EXPENSE; }
        public String toString() { return code + " - " + name; }
    }
    public static final class Line {
        public final String acc; public final double dr, cr; public final String memo;
        Line(String acc, double dr, double cr, String memo) { this.acc = acc; this.dr = Ui.r(dr, 3); this.cr = Ui.r(cr, 3); this.memo = memo == null ? "" : memo; }
    }
    public static final class Entry {
        public LocalDateTime date; public String ref = "", desc = ""; public long refId; public String no = ""; public boolean manual;
        public final List<Line> lines = new ArrayList<>();
        void add(String acc, double amount, String memo) { // موجب = مدين، سالب = دائن
            if (Math.abs(amount) < 0.0005) return;
            lines.add(amount > 0 ? new Line(acc, amount, 0, memo) : new Line(acc, 0, -amount, memo));
        }
        void dr(String acc, double a) { add(acc, a, ""); } void cr(String acc, double a) { add(acc, -a, ""); }
        public double debit() { double s = 0; for (Line l : lines) s += l.dr; return Ui.r(s, 3); }
        public double credit() { double s = 0; for (Line l : lines) s += l.cr; return Ui.r(s, 3); }
        /** تصحيح فروق التقريب (≤ 0.005) على حساب الإيراد/المصروف الأخير */
        void balance(String acc) { double d = Ui.r(debit() - credit(), 3); if (Math.abs(d) >= 0.0005 && Math.abs(d) <= 0.005) add(acc, -d, "فرق تقريب"); }
    }

    // ============================== دليل الحسابات ==============================
    public static final String AR = "AR", INV = "INV", AP = "AP", TAX = "TAX", CAP = "CAP", DRAW = "DRAW", OPEN = "OPEN",
            SALES = "SALES", SALES_RET = "SALES_RET", OTHER = "OTHER", COGS = "COGS", INVDIFF = "INVDIFF", PURCH_DISC = "PURCH_DISC";

    public static List<Acc> chart() {
        List<Acc> l = new ArrayList<>();
        int i = 0;
        for (Wallet w : db().wallets) l.add(new Acc("CASH:" + w.id, "11" + String.format("%02d", ++i), "الصندوق والبنوك - " + w.name, ASSET, true));
        l.add(new Acc(AR, "1200", "ذمم العملاء (المدينون)", ASSET, true));
        l.add(new Acc(INV, "1300", "المخزون", ASSET, true));
        l.add(new Acc(AP, "2100", "ذمم الموردين (الدائنون)", LIABILITY, true));
        l.add(new Acc(TAX, "2200", "ضريبة المبيعات (صافي المستحق)", LIABILITY, true));
        l.add(new Acc(CAP, "3100", "رأس المال", EQUITY, true));
        l.add(new Acc(DRAW, "3150", "جاري المالك (المسحوبات)", EQUITY, true));
        l.add(new Acc(OPEN, "3200", "أرصدة افتتاحية", EQUITY, true));
        l.add(new Acc(SALES, "4100", "المبيعات", REVENUE, true));
        l.add(new Acc(SALES_RET, "4150", "مردودات المبيعات", REVENUE, true));
        l.add(new Acc(PURCH_DISC, "4180", "فروقات أسعار المشتريات المرتجعة", REVENUE, true));
        l.add(new Acc(OTHER, "4200", "إيرادات أخرى", REVENUE, true));
        l.add(new Acc(COGS, "5100", "تكلفة البضاعة المباعة", EXPENSE, true));
        l.add(new Acc(INVDIFF, "5200", "فروقات الجرد والتالف", EXPENSE, true));
        for (ExpenseCat c : db().expenseCats) l.add(new Acc("EXP:" + c.id, String.valueOf(6000 + c.id), "مصروف - " + c.name, EXPENSE, true));
        for (Account a : db().accounts) l.add(new Acc("U:" + a.id, a.code, a.name + (a.active ? "" : " (موقوف)"), a.type, false));
        l.sort(Comparator.comparing(a -> a.code));
        return l;
    }
    public static Map<String, Acc> chartMap() { Map<String, Acc> m = new LinkedHashMap<>(); for (Acc a : chart()) m.put(a.key, a); return m; }
    public static String accName(Map<String, Acc> m, String key) { Acc a = m.get(key); return a == null ? key : a.toString(); }

    // ============================== توليد القيود ==============================
    static String cashKey(long wallet) { return "CASH:" + (wallet == 0 ? db().defaultWallet() : wallet); }
    static String partyAcc(int kind) { return kind == Model.CUSTOMER ? AR : AP; }

    /** تاريخ الإلغاء = آخر حركة مرتبطة بالمستند */
    private static Map<String, LocalDateTime> lastDates;
    static LocalDateTime cancelDate(String ref, long refId, LocalDateTime def) {
        if (lastDates == null) { Map<String, LocalDateTime> m = new HashMap<>();
            for (CashMove c : db().cash) if (c.refId != 0) m.merge(c.ref + "|" + c.refId, c.date, (x, y) -> x.isAfter(y) ? x : y);
            for (StockMove x : db().stock) if (x.refId != 0) m.merge(x.ref + "|" + x.refId, x.date, (p, q) -> p.isAfter(q) ? p : q);
            for (Ledger g : db().ledger) if (g.refId != 0) m.merge(g.ref + "|" + g.refId, g.date, (p, q) -> p.isAfter(q) ? p : q);
            lastDates = m; }
        LocalDateTime d = lastDates.get(ref + "|" + refId); return d == null || d.isBefore(def) ? def : d;
    }

    static Entry reverse(Entry e, LocalDateTime d) {
        Entry r = new Entry(); r.date = d; r.ref = e.ref; r.refId = e.refId; r.no = e.no; r.desc = "إلغاء: " + e.desc;
        for (Line l : e.lines) r.lines.add(new Line(l.acc, l.cr, l.dr, l.memo)); return r;
    }

    static Entry invoiceEntry(Invoice inv) {
        Entry e = new Entry(); e.date = inv.date; e.ref = inv.type.name(); e.refId = inv.id; e.no = String.valueOf(inv.no);
        String who = inv.partyName == null || inv.partyName.isEmpty() ? "" : " - " + inv.partyName;
        e.desc = inv.type.title + " رقم " + inv.no + who;
        double second = Math.min(inv.paid2, inv.paid), first = Ui.r(inv.paid - second, 3);
        String w1 = cashKey(inv.walletId), w2 = cashKey(inv.wallet2 == 0 ? inv.walletId : inv.wallet2);
        double net = Ui.r(inv.total - inv.tax, 3), credit = Ui.r(inv.total - inv.paid, 3), cost = inv.costTotal();
        switch (inv.type) {
            case SALE:
                e.dr(w1, first); e.dr(w2, second); if (inv.partyId != 0) e.dr(AR, credit);
                e.cr(SALES, net); e.cr(TAX, inv.tax);
                e.dr(COGS, cost); e.cr(INV, cost); e.balance(SALES); break;
            case SALE_RETURN:
                e.dr(SALES_RET, net); e.dr(TAX, inv.tax);
                e.cr(w1, first); e.cr(w2, second); if (inv.partyId != 0) e.cr(AR, credit);
                e.dr(INV, cost); e.cr(COGS, cost); e.balance(SALES_RET); break;
            case PURCHASE:
                e.dr(INV, net); e.dr(TAX, inv.tax);
                e.cr(w1, first); e.cr(w2, second); if (inv.partyId != 0) e.cr(AP, credit); e.balance(INV); break;
            case PURCHASE_RETURN: {
                e.dr(w1, first); e.dr(w2, second); if (inv.partyId != 0) e.dr(AP, credit);
                e.cr(TAX, inv.tax);
                // المخزون يخرج بتكلفته المسجّلة، والفرق عن قيمة المرتجع يذهب لفروقات الأسعار
                e.cr(INV, cost); e.add(PURCH_DISC, -(net - cost), "فرق تكلفة المرتجع"); e.balance(PURCH_DISC); break; }
            default: return null;
        }
        return e;
    }

    /** كل القيود (المولّدة + اليدوية) مرتبة بالتاريخ */
    public static synchronized List<Entry> all() {
        lastDates = null;
        List<Entry> out = new ArrayList<>();
        // الفواتير
        for (Invoice inv : db().invoices) { if (inv.type.noEffect()) continue;
            Entry e = invoiceEntry(inv); if (e == null || e.lines.isEmpty()) continue; out.add(e);
            if (inv.cancelled) out.add(reverse(e, cancelDate(inv.type.name(), inv.id, inv.date))); }
        // السندات
        for (Voucher v : db().vouchers) {
            if (v.cancelled) continue; // السند الملغى يختفي كلياً من المحاسبة
            Entry e = new Entry(); e.date = v.date; e.ref = v.in ? "RECEIPT" : "PAYMENT"; e.refId = v.id; e.no = String.valueOf(v.no);
            e.desc = (v.in ? "سند قبض" : "سند صرف") + " رقم " + v.no + " - " + db().partyName(v.partyId) + (v.notes == null || v.notes.trim().isEmpty() ? "" : " - " + v.notes.trim());
            String pa = v.partyId == 0 ? OTHER : partyAcc(v.partyKind);
            if (v.in) { e.dr(cashKey(v.walletId), v.amount); e.cr(pa, v.amount); } else { e.dr(pa, v.amount); e.cr(cashKey(v.walletId), v.amount); }
            out.add(e); }
        // المصروفات
        for (Expense x : db().expenses) { ExpenseCat c = db().expenseCat(x.catId);
            Entry e = new Entry(); e.date = x.date; e.ref = "EXPENSE"; e.refId = x.id; e.no = String.valueOf(x.id);
            e.desc = "مصروف: " + (c == null ? "" : c.name) + (x.notes == null || x.notes.trim().isEmpty() ? "" : " - " + x.notes.trim());
            e.dr("EXP:" + x.catId, x.amount); e.cr(cashKey(x.walletId), x.amount);
            out.add(e); if (x.cancelled) out.add(reverse(e, cancelDate("EXPENSE", x.id, x.date))); }
        // حركات الصندوق المستقلة: افتتاحي، إيداع، سحب، تحويل بين المحافظ
        List<CashMove> tr = new ArrayList<>();
        for (CashMove c : db().cash) {
            if (Math.abs(c.amount) < 0.0005) continue;
            switch (c.ref) {
                case "OPEN": { Entry e = mk(c.date, "OPEN", "رصيد افتتاحي للصندوق - " + db().walletName(c.walletId) + note(c.notes)); e.add(cashKey(c.walletId), c.amount, ""); e.add(OPEN, -c.amount, ""); out.add(e); break; }
                case "DEPOSIT": { Entry e = mk(c.date, "DEPOSIT", "إيداع في " + db().walletName(c.walletId) + note(c.notes)); e.dr(cashKey(c.walletId), c.amount); e.cr(CAP, c.amount); out.add(e); break; }
                case "WITHDRAW": { Entry e = mk(c.date, "WITHDRAW", "سحب من " + db().walletName(c.walletId) + note(c.notes)); e.dr(DRAW, -c.amount); e.cr(cashKey(c.walletId), -c.amount); out.add(e); break; }
                case "TRANSFER": tr.add(c); break;
                default:
            }
        }
        for (int i = 0; i < tr.size(); i++) { CashMove a = tr.get(i); Entry e = mk(a.date, "TRANSFER", a.notes == null ? "تحويل بين المحافظ" : a.notes.trim());
            e.add(cashKey(a.walletId), a.amount, "");
            if (i + 1 < tr.size() && Math.abs(tr.get(i + 1).amount + a.amount) < 0.0005) { CashMove b = tr.get(++i); e.add(cashKey(b.walletId), b.amount, ""); e.desc = "تحويل بين المحافظ: " + db().walletName(a.amount < 0 ? a.walletId : b.walletId) + " ← " + db().walletName(a.amount < 0 ? b.walletId : a.walletId); }
            else e.add(OTHER, -a.amount, "");
            out.add(e); }
        // أرصدة العملاء والموردين الافتتاحية
        for (Ledger g : db().ledger) if ("OPEN".equals(g.ref)) {
            Entry e = mk(g.date, "OPEN", "رصيد افتتاحي - " + db().partyName(g.partyId)); String pa = partyAcc(g.partyKind);
            double net = Ui.r(g.debit - g.credit, 3); e.add(pa, net, ""); e.add(OPEN, -net, ""); out.add(e); }
        // المخزون: افتتاحي وتسويات وتالف
        Map<String, Entry> grp = new LinkedHashMap<>();
        for (StockMove s : db().stock) {
            double v = Ui.r(s.qty * s.cost, 3); if (Math.abs(v) < 0.0005) continue;
            String k; String desc; String contra;
            switch (s.ref) {
                case "OPEN": k = "OPEN|" + s.date.withNano(0); desc = "رصيد مخزون افتتاحي"; contra = OPEN; break;
                case "ADJUST": k = "ADJ|" + s.date.toLocalDate() + "|" + s.notes; desc = s.notes == null || s.notes.isEmpty() ? "تسوية جرد" : s.notes; contra = INVDIFF; break;
                case "DAMAGE": k = "DMG|" + s.refId + "|" + (s.qty > 0); desc = s.qty < 0 ? "تالف رقم " + s.refId + note(s.notes) : "إلغاء تالف رقم " + s.refId; contra = INVDIFF; break;
                default: continue;
            }
            Entry e = grp.get(k); if (e == null) { e = mk(s.date, s.ref, desc); e.refId = s.refId; grp.put(k, e); }
            e.add(INV, v, ""); e.add(contra, -v, "");
        }
        for (Entry e : grp.values()) out.add(merge(e));
        // القيود اليدوية
        for (JournalEntry j : db().journal) { Entry e = manual(j); out.add(e); if (j.cancelled) { Entry r = reverse(e, e.date); r.manual = true; out.add(r); } }
        out.sort(Comparator.comparing((Entry e) -> e.date));
        lastDates = null;
        return out;
    }
    static String note(String n) { return n == null || n.trim().isEmpty() ? "" : " - " + n.trim(); }
    static Entry mk(LocalDateTime d, String ref, String desc) { Entry e = new Entry(); e.date = d; e.ref = ref; e.desc = desc; return e; }
    /** دمج الأسطر المتكررة لنفس الحساب */
    static Entry merge(Entry e) {
        Map<String, Double> m = new LinkedHashMap<>(); for (Line l : e.lines) m.merge(l.acc, l.dr - l.cr, Double::sum);
        Entry r = mk(e.date, e.ref, e.desc); r.refId = e.refId; for (Map.Entry<String, Double> x : m.entrySet()) r.add(x.getKey(), x.getValue(), ""); return r;
    }

    public static Entry manual(JournalEntry j) {
        Entry e = mk(j.date, "JOURNAL", "قيد يدوي رقم " + j.no + note(j.notes)); e.refId = j.id; e.no = String.valueOf(j.no); e.manual = true;
        for (String[] a : parse(j.lines)) e.lines.add(new Line(a[0], Ui.num(a[1]), Ui.num(a[2]), a[3]));
        return e;
    }
    /** الحسابات المربوطة بدفاتر فرعية (الصناديق، العملاء، الموردون، المخزون) لا تُستخدم في القيد اليدوي */
    public static boolean manualAllowed(String key) { return !(key.startsWith("CASH:") || key.equals(AR) || key.equals(AP) || key.equals(INV)); }

    public static List<String[]> parse(String s) {
        List<String[]> l = new ArrayList<>(); if (s == null) return l;
        for (String x : s.split(";")) { if (x.trim().isEmpty()) continue; String[] p = x.split("\\|", -1); if (p.length < 3) continue;
            l.add(new String[]{p[0], p[1], p[2], p.length > 3 ? p[3] : ""}); }
        return l;
    }
    static String clean(String s) { return s == null ? "" : s.replace("|", "/").replace(";", ",").replace("\n", " ").trim(); }

    /** حفظ قيد يدوي — يجب أن يتوازن */
    public static JournalEntry saveManual(LocalDateTime date, String notes, List<String[]> lines) {
        double d = 0, c = 0; StringBuilder b = new StringBuilder(); Map<String, Acc> m = chartMap(); int n = 0;
        for (String[] a : lines) {
            double dr = Ui.r(Ui.num(a[1]), 3), cr = Ui.r(Ui.num(a[2]), 3);
            if (dr == 0 && cr == 0) continue;
            if (dr < 0 || cr < 0) throw new Ops.OpException("المبالغ يجب أن تكون موجبة");
            if (dr > 0 && cr > 0) throw new Ops.OpException("السطر لا يكون مديناً ودائناً معاً");
            if (!m.containsKey(a[0])) throw new Ops.OpException("اختر الحساب لكل سطر");
            if (!manualAllowed(a[0])) throw new Ops.OpException("لا يمكن استخدام حساب «" + m.get(a[0]).name + "» في قيد يدوي.\n"
                    + "هذا الحساب مرتبط بحركات البرنامج: استخدم سند قبض/صرف، أو إيداع/سحب، أو الجرد، حتى تبقى أرصدة الصناديق والعملاء والموردين والمخزون مطابقة.");
            d += dr; c += cr; n++;
            b.append(a[0]).append('|').append(dr).append('|').append(cr).append('|').append(clean(a[3])).append(';');
        }
        if (n < 2) throw new Ops.OpException("القيد يحتاج سطرين على الأقل");
        if (Math.abs(d - c) > 0.0005) throw new Ops.OpException("القيد غير متوازن\nالمدين: " + Ui.m(d) + "   الدائن: " + Ui.m(c) + "\nالفرق: " + Ui.m(Math.abs(d - c)));
        JournalEntry j = new JournalEntry(); j.id = db().next("journal"); j.no = db().next("no_JOURNAL"); j.date = date; j.notes = clean(notes); j.lines = b.toString(); j.user = Session.name();
        db().journal.add(j); Store.save(); return j;
    }

    // ============================== الأرصدة ==============================
    /** مجموع المدين والدائن لكل حساب بين تاريخين (null = بلا حد) */
    public static Map<String, double[]> totals(List<Entry> es, LocalDate from, LocalDate to) {
        Map<String, double[]> m = new HashMap<>();
        for (Entry e : es) { if (from != null && e.date.isBefore(Ui.start(from))) continue; if (to != null && !e.date.isBefore(Ui.end(to))) continue;
            for (Line l : e.lines) { double[] a = m.computeIfAbsent(l.acc, k -> new double[2]); a[0] += l.dr; a[1] += l.cr; } }
        return m;
    }
    /** الرصيد بحسب طبيعة الحساب (موجب = طبيعي) */
    public static double bal(Acc a, double[] t) { if (t == null) return 0; return Ui.r(a.debitNature() ? t[0] - t[1] : t[1] - t[0], 3); }
}
