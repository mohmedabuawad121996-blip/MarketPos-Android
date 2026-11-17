package com.market;

import com.market.Model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** المستخدمون وتسجيل الدخول والصلاحيات */
public final class Session {
    private Session() {}
    public static User user;

    public static String name() { return user == null ? "" : user.name; }
    public static boolean admin() { return user == null || user.admin(); }

    /** العمليات المسموحة للكاشير (الباقي للمدير فقط) */
    static final Set<String> CASHIER = new HashSet<>(Arrays.asList(
            "نقطة البيع (فاتورة مبيعات)", "مرتجع مبيعات", "عرض سعر", "إضافة عميل جديد", "المبالغ النقدية (سند قبض)", "عرض العملاء",
            "الذمم (الفواتير الآجلة)", "فحص أرصدة العملاء", "استعلام عن سعر", "تسجيل مصروف", "عرض المنتجات", "كشف حساب عميل", "المظهر والخط والثيمات", "الورديات وإغلاق الصندوق", "طباعة ملصقات الباركود", "شاشة الزبون (تشغيل / إيقاف)", "تواريخ الصلاحية"));
    /** القدرات الافتراضية للكاشير (كما في الإصدارات السابقة) */
    static final List<String> DEFAULT_CAPS = Arrays.asList("cap:discount", "cap:price", "cap:delete", "cap:credit", "cap:drawer");
    public static Set<String> defaults() { Set<String> s = new HashSet<>(CASHIER); s.addAll(DEFAULT_CAPS); return s; }
    public static Set<String> permsOf(User u) {
        if (u == null || u.perms == null || u.perms.trim().isEmpty()) return defaults();
        Set<String> s = new HashSet<>(); for (String x : u.perms.split("\n")) if (!x.trim().isEmpty() && !x.trim().equals("-")) s.add(x.trim()); return s;
    }
    private static Set<String> permCache; private static User permUser; private static String permRaw;
    static Set<String> perms() {
        if (permUser != user || permCache == null || !Objects.equals(permRaw, user == null ? null : user.perms)) { permUser = user; permRaw = user == null ? null : user.perms; permCache = permsOf(user); }
        return permCache;
    }
    public static boolean can(String action) { return admin() || perms().contains(action); }
    /** قدرة داخل الشاشات: cap:discount, cap:price, cap:delete, cap:credit, cap:cost, cap:cancel */
    public static boolean cap(String c) { return admin() || perms().contains(c); }

    // ------------------------------------------------ كلمات المرور (PBKDF2)
    public static String hash(String pass) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(pass, salt));
    }
    public static boolean verify(String pass, String stored) {
        if (stored == null || stored.isEmpty()) return pass.isEmpty();
        String[] p = stored.split(":"); if (p.length != 2) return false;
        byte[] a = pbkdf2(pass, Base64.getDecoder().decode(p[0])), b = Base64.getDecoder().decode(p[1]);
        return java.security.MessageDigest.isEqual(a, b);
    }
    static byte[] pbkdf2(String pass, byte[] salt) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(pass.toCharArray(), salt, 60000, 256)).getEncoded(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    /** إنشاء المدير الافتراضي (مع ترحيل كلمة المرور القديمة إن وُجدت) */
    public static void ensureAdmin() {
        Store s = Store.db;
        if (!s.users.isEmpty()) return;
        User u = new User(); u.id = s.next("user"); u.name = "المدير"; u.username = "admin"; u.role = "مدير";
        String old = s.get("password", "");
        u.passHash = old.isEmpty() ? "" : hash(old);
        s.users.add(u); s.settings.remove("password"); Store.save();
    }

    public static User login(String username, String pass) {
        for (User u : Store.db.users) if (u.active && u.username.equalsIgnoreCase(username.trim()) && verify(pass, u.passHash)) {
            u.lastLogin = LocalDateTime.now(); user = u; Store.db.set("lastUser", u.username); Store.save(); return u;
        }
        return null;
    }
}
