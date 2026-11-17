package com.market;

import com.market.Model.*;
import java.util.*;

/** نقاط الولاء: نقطة لكل مبلغ مشتريات، تُستبدل خصماً على فاتورة لاحقة */
public final class Loyalty {
    private Loyalty() {}
    static Store db() { return Store.db; }
    public static boolean enabled() { return db().getB("pointsOn", true); }
    public static double per() { return Math.max(0.001, db().getD("pointsPer", 1)); }
    /** قيمة النقطة بالدينار */
    public static double value() { return Math.max(0, db().getD("pointValue", 0.01)); }
    public static long minRedeem() { return (long) Math.max(1, db().getD("pointsMin", 100)); }

    /** {المكتسب، المستبدل} لكل عميل */
    public static Map<Long, double[]> all() {
        Map<Long, double[]> net = new HashMap<>();
        for (Invoice i : db().invoices) { if (i.cancelled || i.partyId == 0) continue;
            if (i.type == InvType.SALE) { double[] a = net.computeIfAbsent(i.partyId, k -> new double[2]); a[0] += i.total; a[1] += i.pointsUsed; }
            else if (i.type == InvType.SALE_RETURN) net.computeIfAbsent(i.partyId, k -> new double[2])[0] -= i.total; }
        Map<Long, double[]> r = new HashMap<>();
        for (Map.Entry<Long, double[]> e : net.entrySet()) r.put(e.getKey(), new double[]{Math.max(0, Math.floor(e.getValue()[0] / per() + 1e-9)), e.getValue()[1]});
        return r;
    }
    public static long earned(long party) { double[] a = all().get(party); return a == null ? 0 : (long) a[0]; }
    public static long balance(long party) { double[] a = all().get(party); return a == null ? 0 : (long) Math.max(0, a[0] - a[1]); }
    public static long balance(long party, Map<Long, double[]> m) { double[] a = m.get(party); return a == null ? 0 : (long) Math.max(0, a[0] - a[1]); }

    /** تحقق قبل حفظ فاتورة فيها استبدال نقاط */
    static void check(Invoice inv) {
        if (inv.pointsUsed <= 0) return;
        if (!enabled()) throw new Ops.OpException("نظام النقاط غير مفعّل من الإعدادات");
        if (inv.type != InvType.SALE) throw new Ops.OpException("استبدال النقاط في فواتير البيع فقط");
        if (inv.partyId == 0) throw new Ops.OpException("اختر العميل لاستبدال النقاط");
        long b = balance(inv.partyId);
        if (inv.pointsUsed > b) throw new Ops.OpException("نقاط العميل غير كافية. الرصيد: " + b + " نقطة");
        if (inv.pointsUsed < minRedeem()) throw new Ops.OpException("أقل عدد نقاط للاستبدال: " + minRedeem());
    }
}
