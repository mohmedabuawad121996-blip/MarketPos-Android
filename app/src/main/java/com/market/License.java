package com.market;

import java.time.LocalDate;

/** ترخيص نسخة الهاتف: فترة تجريبية 30 يوماً من تاريخ أول تشغيل، أو مفعّل */
public final class License {
    private License() {}
    public static final int TRIAL_DAYS = 30;
    public static void resetCache() {}
    public static boolean isActivated() { return Store.db != null && Store.db.getB("mobileActivated", false); }
    public static long daysLeft() {
        if (Store.db == null) return TRIAL_DAYS;
        LocalDate first; try { first = LocalDate.parse(Store.db.get("installDate", LocalDate.now().toString())); } catch (Exception e) { first = LocalDate.now(); }
        long used = LocalDate.now().toEpochDay() - first.toEpochDay();
        return Math.max(0, TRIAL_DAYS - used);
    }
    public static boolean canOperate() { return isActivated() || daysLeft() > 0; }
}
