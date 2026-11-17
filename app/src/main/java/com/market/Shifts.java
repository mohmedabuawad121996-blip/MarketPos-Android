package com.market;

import com.market.Model.*;
import java.time.LocalDateTime;

/** الورديات: نفس منطق نسخة الكمبيوتر (ShiftUI) بدون واجهة */
public final class Shifts {
    private Shifts() {}
    static final double[] DENOMS = {50, 20, 10, 5, 1, 0.5, 0.25, 0.1, 0.05};

    static Store db() { return Store.db; }
    public static boolean enabled() { return db().getB("shiftsEnabled", true); }

    /** الوردية المفتوحة للمستخدم (أو null) */
    public static Shift current(String user) {
        for (int i = db().shifts.size() - 1; i >= 0; i--) { Shift s = db().shifts.get(i); if (s.isOpen() && s.user.equals(user)) return s; }
        return null;
    }
    public static Shift current() { return current(Session.name()); }

    static boolean cashWallet(long id) {
        if (id == 0) id = db().defaultWallet();
        for (Wallet w : db().wallets) if (w.id == id) return w.type == null || w.type.equals("نقدي") || id == db().defaultWallet();
        return id == db().defaultWallet();
    }

    static boolean in(LocalDateTime d, Shift s, LocalDateTime end) { return d != null && !d.isBefore(s.opened) && !d.isAfter(end); }

    /** حساب أرقام الوردية حتى لحظة end (أو الآن) */
    public static Shift compute(Shift s) {
        LocalDateTime end = s.closed != null ? s.closed : LocalDateTime.now();
        s.sales = s.returns = s.cashSales = s.cardSales = s.creditSales = s.receipts = s.payments = s.expenses = 0; s.invoiceCount = 0;
        double cashIn = 0;
        for (Invoice i : db().invoices) {
            if (i.cancelled || i.type.noEffect() || !s.user.equals(i.user == null ? "" : i.user) || !in(i.date, s, end)) continue;
            double cashPart = cashWallet(i.walletId) ? i.paid - i.paid2 : 0;
            double otherPart = (i.paid - i.paid2) - cashPart + i.paid2;
            if (i.type == InvType.SALE) {
                s.invoiceCount++; s.sales += i.total; s.cashSales += cashPart; s.cardSales += otherPart; s.creditSales += i.total - i.paid;
                cashIn += cashPart;
            } else if (i.type == InvType.SALE_RETURN) { s.returns += i.total; cashIn -= cashPart; }
            else cashIn += i.type.cashSign() * cashPart; // مشتريات أو مرتجع مشتريات نقداً من الدرج
        }
        for (Voucher v : db().vouchers) {
            if (v.cancelled || !s.user.equals(v.user == null ? "" : v.user) || !in(v.date, s, end) || !cashWallet(v.walletId)) continue;
            if (v.in) { s.receipts += v.amount; cashIn += v.amount; } else { s.payments += v.amount; cashIn -= v.amount; }
        }
        for (Expense e : db().expenses) {
            if (e.cancelled || !s.user.equals(e.user == null ? "" : e.user) || !in(e.date, s, end) || !cashWallet(e.walletId)) continue;
            s.expenses += e.amount; cashIn -= e.amount;
        }
        s.sales = Ui.r(s.sales); s.returns = Ui.r(s.returns); s.cashSales = Ui.r(s.cashSales); s.cardSales = Ui.r(s.cardSales);
        s.creditSales = Ui.r(s.creditSales); s.receipts = Ui.r(s.receipts); s.payments = Ui.r(s.payments); s.expenses = Ui.r(s.expenses);
        s.expectedCash = Ui.r(s.openingCash + cashIn);
        if (s.closed != null) s.diff = Ui.r(s.countedCash - s.expectedCash);
        return s;
    }

    public static Shift open(double openingCash) {
        if (current() != null) throw new Ops.OpException("لديك وردية مفتوحة بالفعل");
        Shift s = new Shift(); s.id = db().next("shift"); s.no = db().next("no_SHIFT"); s.user = Session.name(); s.openingCash = Ui.r(openingCash);
        s.opened = LocalDateTime.now(); db().shifts.add(s); Store.save(); return s;
    }

    public static void close(Shift s, double counted, String counts, String notes) {
        s.closed = LocalDateTime.now(); s.countedCash = Ui.r(counted); s.counts = counts == null ? "" : counts; s.notes = notes == null ? "" : notes;
        compute(s); Store.save();
    }

    /** آخر مبلغ تُرك في الدرج (لاقتراحه كرصيد بداية) */
    public static double lastFloat() {
        for (int i = db().shifts.size() - 1; i >= 0; i--) { Shift s = db().shifts.get(i); if (!s.isOpen()) return Math.max(0, s.countedCash - db().getD("shiftDeposit_" + s.id, 0)); }
        return db().getD("defaultFloat", 0);
    }

}
