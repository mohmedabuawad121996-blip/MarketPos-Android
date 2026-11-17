package com.market;

import com.market.Model.*;

/**
 * باركود الميزان (EAN-13 يبدأ بـ 2): PP CCCCC VVVVV K
 * PP = بادئة الميزان، CCCCC = رقم الصنف في الميزان (PLU)، VVVVV = الوزن بالغرام أو السعر بالفلس، K = رقم التحقق.
 */
public final class Scale {
    private Scale() {}
    public static final class Hit { public Product p; public double qty, value; public boolean byPrice; public String plu; }

    static Store db() { return Store.db; }
    public static boolean enabled() { return db().getB("scaleOn", true); }
    static String prefixes() { return db().get("scalePrefixes", "20,21,22,23,24,25,26,27,28,29"); }
    public static int codeLen() { return (int) Math.max(4, Math.min(6, db().getD("scaleCodeLen", 5))); }
    /** weight أو price */
    static String mode() { return db().get("scaleMode", "weight"); }
    static int decimals() { return (int) db().getD("scaleDecimals", 3); }

    public static boolean looksLike(String code) {
        if (!enabled() || code == null || !code.matches("\\d{13}")) return false;
        for (String p : prefixes().split("[,،\\s]+")) if (!p.isEmpty() && code.startsWith(p.trim())) return true;
        return false;
    }
    static boolean checkDigitOk(String c) {
        int s = 0; for (int i = 0; i < 12; i++) s += (c.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - s % 10) % 10 == c.charAt(12) - '0';
    }

    /** يقرأ باركود الميزان. null إن لم يكن باركود ميزان أو لم يوجد الصنف */
    public static Hit parse(String code) {
        if (!looksLike(code)) return null;
        int pl = 2, cl = codeLen();
        String plu = code.substring(pl, pl + cl), val = code.substring(pl + cl, 12);
        Product p = byPlu(plu); if (p == null) return null;
        Hit h = new Hit(); h.p = p; h.plu = plu;
        double v = Long.parseLong(val) / Math.pow(10, decimals());
        h.byPrice = "price".equals(mode());
        if (h.byPrice) { h.value = v; h.qty = p.price > 0 ? Ui.r(v / p.price, 3) : 1; }
        else { h.qty = Ui.r(v, 3); h.value = Ui.r(h.qty * p.price, 3); }
        if (h.qty <= 0) return null;
        return h;
    }

    /** البحث برقم الميزان: حقل PLU، ثم الباركود نفسه (مع أو بدون الأصفار البادئة) */
    public static Product byPlu(String plu) {
        String n = strip(plu);
        for (Product p : db().products.values()) if (p.active && p.plu != null && !p.plu.trim().isEmpty() && strip(p.plu.trim()).equals(n)) return p;
        Product p = db().byBarcode(plu); if (p != null) return p;
        return db().byBarcode(n);
    }
    static String strip(String s) { String r = s.replaceFirst("^0+", ""); return r.isEmpty() ? "0" : r; }

    /** يولّد باركود ميزان (للاختبار ولطباعة ملصقات وزن من البرنامج) */
    public static String make(String prefix, String plu, double value) {
        int cl = codeLen(); String c = prefix + String.format("%0" + cl + "d", Long.parseLong(strip(plu)));
        long v = Math.round(value * Math.pow(10, decimals()));
        c += String.format("%0" + (10 - cl) + "d", v);
        int s = 0; for (int i = 0; i < 12; i++) s += (c.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return c + (10 - s % 10) % 10;
    }
}
