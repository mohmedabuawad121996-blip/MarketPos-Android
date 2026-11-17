package com.market;

/** أدوات نصية للذمم (نسخة الهاتف) */
public final class PartyUI {
    private PartyUI() {}
    static String noun(int k) { return k == Model.CUSTOMER ? "عميل" : "مورد"; }
    static String nouns(int k) { return k == Model.CUSTOMER ? "العملاء" : "الموردين"; }
    static boolean isDebit(int kind, double signed) { return kind == Model.CUSTOMER ? signed >= 0 : signed < 0; }
    static double signed(int kind, double amount, boolean debit) { return (kind == Model.CUSTOMER) == debit ? amount : -amount; }
    public static String sideText(int kind, double signed) { return Math.abs(signed) < 0.0005 ? "متوازن" : isDebit(kind, signed) ? "مدين" : "دائن"; }
}
