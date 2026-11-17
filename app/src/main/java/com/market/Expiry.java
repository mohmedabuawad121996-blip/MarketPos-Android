package com.market;

import com.market.Model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;

/**
 * تواريخ الصلاحية: كل فاتورة شراء بتاريخ صلاحية تنشئ دفعة.
 * الكمية المتبقية من كل دفعة تُقدَّر بأن المخزون الحالي هو آخر ما دخل (يُباع الأقدم أولاً).
 */
public final class Expiry {
    private Expiry() {}
    static Store db() { return Store.db; }
    public static int alertDays() { return (int) Math.max(1, db().getD("expiryDays", 30)); }

    /** يقبل 2027-05-31 أو 31/5/2027 أو 31-5-27 أو 5/2027 (آخر الشهر). "" يعني بدون تاريخ، null خطأ */
    public static String parse(String s) {
        if (s == null) return ""; s = s.trim().replace('\\', '/').replace('.', '/');
        if (s.isEmpty()) return "";
        try {
            if (s.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) { String[] p = s.split("-"); return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).toString(); }
            String[] p = s.split("[/-]");
            if (p.length == 3) { int y = Integer.parseInt(p[2]); if (y < 100) y += 2000; return LocalDate.of(y, Integer.parseInt(p[1]), Integer.parseInt(p[0])).toString(); }
            if (p.length == 2) { int y = Integer.parseInt(p[1]); if (y < 100) y += 2000; LocalDate d = LocalDate.of(y, Integer.parseInt(p[0]), 1); return d.withDayOfMonth(d.lengthOfMonth()).toString(); }
        } catch (Exception qx) { Store.logQuiet("expiry", qx); }
        return null;
    }

    public static Batch addBatch(Product p, double qty, String expiry, long invoiceId, long wh, LocalDateTime date) {
        Batch b = new Batch(); b.id = db().next("batch"); b.productId = p.id; b.qty = qty; b.expiry = expiry; b.invoiceId = invoiceId; b.warehouseId = wh; b.date = date == null ? LocalDateTime.now() : date;
        db().batches.add(b); return b;
    }

    public static final class Row { public Batch b; public Product p; public double left; public long days; }

    /** الدفعات التي ما زال منها كمية في المخزون */
    public static List<Row> open() {
        Map<Long, List<Batch>> by = new HashMap<>();
        for (Batch b : db().batches) if (!b.closed && b.expiry != null && !b.expiry.isEmpty()) by.computeIfAbsent(b.productId, k -> new ArrayList<>()).add(b);
        // مشتريات بدون تاريخ صلاحية للأصناف نفسها: تدخل في الترتيب (الأحدث يبقى في المخزون) ولا تُعرض
        Map<Long, List<Batch>> plain = new HashMap<>();
        for (Invoice i : db().invoices) if (i.type == InvType.PURCHASE && !i.cancelled) for (InvLine l : i.lines)
            if (by.containsKey(l.productId) && (l.expiry == null || l.expiry.isEmpty())) { Batch x = new Batch(); x.productId = l.productId; x.qty = l.pieces(); x.date = i.date; plain.computeIfAbsent(l.productId, k -> new ArrayList<>()).add(x); }
        List<Row> out = new ArrayList<>(); long today = LocalDate.now().toEpochDay();
        for (Map.Entry<Long, List<Batch>> e : by.entrySet()) {
            Product p = db().products.get(e.getKey()); if (p == null) continue;
            List<Batch> l = new ArrayList<>(e.getValue()); l.addAll(plain.getOrDefault(e.getKey(), Collections.emptyList()));
            l.sort((x, y) -> y.date.compareTo(x.date)); // الأحدث أولاً
            double left = Math.max(0, p.qty);
            for (Batch b : l) { if (left <= 1e-9) break; double take = Math.min(b.qty, left); left -= take;
                if (b.expiry == null || b.expiry.isEmpty()) continue;
                Row r = new Row(); r.b = b; r.p = p; r.left = Ui.r(take, 3); r.days = LocalDate.parse(b.expiry).toEpochDay() - today; out.add(r); }
        }
        out.sort(Comparator.comparingLong(r -> r.days));
        return out;
    }
    /** {منتهي، قريب الانتهاء} */
    public static int[] counts() { int ex = 0, soon = 0; for (Row r : open()) { if (r.days < 0) ex++; else if (r.days <= alertDays()) soon++; } return new int[]{ex, soon}; }

    /** تنبيه مرة واحدة يومياً عند فتح البرنامج */
}
