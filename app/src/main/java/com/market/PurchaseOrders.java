package com.market;

import com.market.Model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** اقتراح الطلبية (نفس منطق نسخة الكمبيوتر) */
public final class PurchaseOrders {
    private PurchaseOrders() {}
    static Store db() { return Store.db; }

    public static final class Sug { public Product p; public long supplier; public double sold, daily, need, cost; }

    /** آخر مورد اشترينا منه كل صنف */
    static Map<Long, Long> lastSupplier() {
        Map<Long, Long> m = new HashMap<>(); Map<Long, LocalDateTime> d = new HashMap<>();
        for (Invoice i : db().invoices) if (i.type == InvType.PURCHASE && !i.cancelled && i.partyId != 0) for (InvLine l : i.lines)
            if (!d.containsKey(l.productId) || i.date.isAfter(d.get(l.productId))) { d.put(l.productId, i.date); m.put(l.productId, i.partyId); }
        return m;
    }

    /** الكمية المقترحة = (متوسط البيع اليومي × أيام التغطية) أو حد الطلب أيهما أكبر، ناقص الموجود. تُقرَّب للكرتونة */
    public static List<Sug> suggest(long supplier, int salesDays, int coverDays) {
        LocalDateTime from = LocalDate.now().minusDays(salesDays).atStartOfDay();
        Map<Long, Double> sold = new HashMap<>();
        for (Invoice i : db().invoices) { if (i.cancelled || i.date.isBefore(from)) continue;
            int s = i.type == InvType.SALE ? 1 : i.type == InvType.SALE_RETURN ? -1 : 0; if (s == 0) continue;
            for (InvLine l : i.lines) sold.merge(l.productId, s * l.pieces(), Double::sum); }
        Map<Long, Long> sup = lastSupplier(); List<Sug> out = new ArrayList<>();
        for (Product p : db().products.values()) { if (!p.active) continue;
            long sp = sup.getOrDefault(p.id, 0L); if (supplier > 0 && sp != supplier) continue;
            double s = Math.max(0, sold.getOrDefault(p.id, 0.0)), daily = s / Math.max(1, salesDays);
            double target = Math.max(p.minQty, daily * coverDays), need = target - p.qty;
            if (need <= 1e-9 || (s <= 0 && p.minQty <= 0)) continue;
            if (p.hasBig()) need = Math.ceil(need / p.factor - 1e-9) * p.factor; else need = Math.ceil(need - 1e-9);
            Sug g = new Sug(); g.p = p; g.supplier = sp; g.sold = s; g.daily = daily; g.need = need; g.cost = p.cost; out.add(g); }
        out.sort((a, b) -> Double.compare(b.need * b.cost, a.need * a.cost));
        return out;
    }

    public static String qtyText(Product p, double q) {
        if (p.hasBig() && q >= p.factor && Math.abs(q / p.factor - Math.round(q / p.factor)) < 1e-9) return Ui.q(q / p.factor) + " " + p.bigUnit + " (" + Ui.q(q) + " " + p.unit + ")";
        return Ui.q(q) + " " + p.unit;
    }

}
