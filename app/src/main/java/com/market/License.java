package com.market;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;

/** ترخيص نسخة الهاتف: فترة تجريبية 30 يوماً، أو تفعيل بمفتاح موقّع (Ed25519) لرقم الجهاز — نفس مولّد المفاتيح لنسخة الكمبيوتر */
public final class License {
    private License() {}
    static final String PUB_HEX = "e96777103b7ed744a13c44d396e83034d749a02460626d90c61c6ab5bafb958b";
    public static final int TRIAL_DAYS = 30;

    /** تخزين دائم خارج قاعدة البيانات (يضبطه التطبيق): لا يضيع التفعيل ولا تُعاد الفترة التجريبية عند استعادة نسخة */
    public interface Prefs { String get(String k); void put(String k, String v); }
    public static volatile Prefs prefs;
    /** معرّف الجهاز الخام (ANDROID_ID) يضبطه التطبيق */
    public static volatile String rawDeviceId = "";

    private static String midCache;
    public static String machineId() {
        if (midCache == null) {
            try { byte[] h = MessageDigest.getInstance("SHA-256").digest(("MP-PHONE|" + rawDeviceId).getBytes(StandardCharsets.UTF_8));
                midCache = group(hex(h).substring(0, 12)); }
            catch (Exception e) { midCache = "000-000-000-000"; }
        }
        return midCache;
    }
    static byte[] actMessage(String machineId) { return ("MP-ACT1|" + machineId.trim().toUpperCase(java.util.Locale.ROOT)).getBytes(StandardCharsets.UTF_8); }

    public static boolean verifyKey(String machineId, String key) {
        try {
            String h = key == null ? "" : key.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(java.util.Locale.ROOT);
            if (h.length() != 128) return false;
            org.bouncycastle.crypto.params.Ed25519PublicKeyParameters pk = new org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(unhex(PUB_HEX), 0);
            org.bouncycastle.crypto.signers.Ed25519Signer s = new org.bouncycastle.crypto.signers.Ed25519Signer();
            s.init(false, pk); byte[] m = actMessage(machineId); s.update(m, 0, m.length);
            return s.verifySignature(unhex(h));
        } catch (Throwable e) { return false; }
    }
    private static Boolean actCache; private static String actKey;
    static String storedKey() {
        String k = prefs == null ? null : prefs.get("licenseKey");
        if ((k == null || k.isEmpty()) && Store.db != null) k = Store.db.get("phoneLicenseKey", "");
        return k == null ? "" : k;
    }
    public static boolean isActivated() {
        String k = storedKey();
        if (actCache != null && k.equals(actKey)) return actCache;
        actKey = k; actCache = !k.trim().isEmpty() && verifyKey(machineId(), k); return actCache;
    }
    public static boolean activate(String key) {
        if (key == null || !verifyKey(machineId(), key)) return false;
        String k = key.trim().toUpperCase(java.util.Locale.ROOT);
        if (prefs != null) prefs.put("licenseKey", k);
        if (Store.db != null) { Store.db.set("phoneLicenseKey", k); Store.save(); }
        actCache = null; return true;
    }
    public static void resetCache() { actCache = null; }

    /** تاريخ أول تشغيل على هذا الهاتف */
    static LocalDate first() {
        String f = prefs == null ? null : prefs.get("firstRun");
        LocalDate d;
        try { d = f == null || f.isEmpty() ? null : LocalDate.parse(f); } catch (Exception e) { d = null; }
        if (d == null) { d = LocalDate.now(); if (prefs != null) prefs.put("firstRun", d.toString()); }
        // منع إرجاع التاريخ: نحفظ آخر تاريخ شوهد
        String seen = prefs == null ? null : prefs.get("lastSeen");
        try { if (seen != null && LocalDate.parse(seen).isAfter(LocalDate.now())) return d.minusDays(TRIAL_DAYS); } catch (Exception ignored) {}
        if (prefs != null) prefs.put("lastSeen", LocalDate.now().toString());
        return d;
    }
    public static long daysLeft() { long used = LocalDate.now().toEpochDay() - first().toEpochDay(); return Math.max(0, TRIAL_DAYS - used); }
    public static long trialLeft() { return daysLeft(); }
    public static boolean canOperate() { return isActivated() || daysLeft() > 0; }
    public static String status() { return isActivated() ? "مفعّل" : daysLeft() > 0 ? "نسخة تجريبية — متبقٍ " + daysLeft() + " يوم" : "انتهت الفترة التجريبية"; }

    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02X", x)); return s.toString(); }
    static byte[] unhex(String s) { byte[] b = new byte[s.length() / 2]; for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16); return b; }
    static String group(String s) { StringBuilder r = new StringBuilder(); for (int i = 0; i < s.length(); i++) { if (i > 0 && i % 4 == 0) r.append('-'); r.append(s.charAt(i)); } return r.toString(); }
}
