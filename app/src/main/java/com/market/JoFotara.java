package com.market;

/** الفوترة الإلكترونية غير مفعّلة في نسخة الهاتف حالياً: لا توجد فاتورة مُرسلة */
public final class JoFotara { private JoFotara() {} public static boolean submitted(Model.Invoice inv) { return false; } }
