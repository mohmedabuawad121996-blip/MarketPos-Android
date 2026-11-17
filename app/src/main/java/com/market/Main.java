package com.market;

/** تسجيل الأخطاء غير المتوقعة في error.log داخل مجلد البيانات */
public final class Main {
    private Main() {}
    public static final String VERSION = "1.0";
    public static void report(Throwable t) { Store.logQuiet("error", t); }
}
