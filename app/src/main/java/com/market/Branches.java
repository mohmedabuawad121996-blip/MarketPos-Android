package com.market;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** الفروع: الهاتف لا يقرأ ملفات الفروع، يعرض فرعه فقط */
public final class Branches {
    private Branches() {}
    static Store db() { return Store.db; }
    public static String name() { return db().get("branchName", db().get("storeName", "الفرع")).trim(); }
    static String code() { return db().get("branchCode", "phone"); }
    public static boolean on() { return false; }
    public static final class Snap {
        public String name, code, version; public LocalDateTime updated; public Path file;
        public final TreeMap<LocalDate, double[]> days = new TreeMap<>(); public final Map<String, Double> wallets = new LinkedHashMap<>();
        public double ar, ap; public final Map<String, String[]> products = new HashMap<>();
        public double sales(LocalDate a, LocalDate b, int k) { double s = 0; for (Map.Entry<LocalDate, double[]> e : days.subMap(a, true, b, true).entrySet()) s += e.getValue()[k]; return s; }
        public double stockValue() { double v = 0; for (String[] p : products.values()) { double q = Double.parseDouble(p[1]); if (q > 0) v += q * Double.parseDouble(p[2]); } return v; }
        public double cash() { double c = 0; for (double v : wallets.values()) c += v; return c; }
    }
    public static List<Snap> read() { return new ArrayList<>(); }
    static String age(LocalDateTime t) {
        if (t == null) return ""; long m = Duration.between(t, LocalDateTime.now()).toMinutes();
        return m < 1 ? "الآن" : m < 60 ? "قبل " + m + " دقيقة" : m < 1440 ? "قبل " + (m / 60) + " ساعة" : "قبل " + (m / 1440) + " يوم";
    }
}
