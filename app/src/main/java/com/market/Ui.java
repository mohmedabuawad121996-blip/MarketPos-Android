package com.market;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** نسخة أندرويد من أدوات الأرقام والتواريخ التي يستخدمها منطق المحاسبة (بلا أي واجهات حاسوب) */
public final class Ui {
    private Ui() {}

    /** تأكيد/تنبيه من منطق المحاسبة: يربطهما التطبيق بنوافذه. الافتراضي: رفض التأكيد وتسجيل الخطأ */
    public interface Asker { boolean confirm(String msg); }
    public interface Teller { void err(String msg); }
    public static volatile Asker asker = msg -> false;
    public static volatile Teller teller = msg -> android.util.Log.e("MarketPro", msg);

    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final DecimalFormatSymbols SYM = DecimalFormatSymbols.getInstance(Locale.US);

    public static int dec() { return Store.db == null ? 3 : (int) Store.db.getD("decimals", 3); }
    public static double r(double v) { return r(v, dec()); }
    public static double r(double v, int d) { double p = Math.pow(10, d); return Math.round(v * p) / p; }
    public static String m(double v) {
        StringBuilder pat = new StringBuilder("#,##0"); int d = dec(); if (d > 0) { pat.append('.'); for (int i = 0; i < d; i++) pat.append('0'); }
        return new DecimalFormat(pat.toString(), SYM).format(r(v));
    }
    public static String q(double v) { return new DecimalFormat("#,##0.###", SYM).format(v); }
    public static double num(String s) {
        if (s == null) return 0; s = s.trim().replace(",", "");
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) { if (c >= '٠' && c <= '٩') b.append((char) ('0' + c - '٠')); else if (c == '٫') b.append('.'); else b.append(c); }
        try { return b.length() == 0 ? 0 : Double.parseDouble(b.toString()); } catch (NumberFormatException e) { return 0; }
    }
    public static String dt(LocalDateTime d) { return d == null ? "" : d.format(DT); }
    public static LocalDateTime start(LocalDate d) { return d.atStartOfDay(); }
    public static LocalDateTime end(LocalDate d) { return d.plusDays(1).atStartOfDay(); }
    public static boolean confirm(String msg) { return asker.confirm(msg); }
    public static void err(String msg) { teller.err(msg); }
}
