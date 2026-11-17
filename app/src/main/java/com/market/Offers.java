package com.market;

import com.market.Model.*;
import java.time.LocalDate;
import java.util.List;

/** العروض: نفس منطق نسخة الكمبيوتر (OffersUI) بدون واجهة */
public final class Offers {
    private Offers() {}
    public static final String[] KINDS = {"كمية بسعر (مثل 3 بـ 1 دينار)", "خصم نسبة %", "سعر خاص للحبة"};

    /** العرض الفعّال اليوم لهذا الصنف (الأحدث أولاً) */
    public static Offer live(long productId, LocalDate d) {
        for (int i = Store.db.offers.size() - 1; i >= 0; i--) { Offer o = Store.db.offers.get(i); if (o.productId == productId && o.liveOn(d)) return o; }
        return null;
    }

    public static String title(Offer o) {
        Product p = Store.db.products.get(o.productId);
        return o.name != null && !o.name.trim().isEmpty() ? o.name.trim() : o.describe(p == null ? "حبة" : p.unit);
    }

    /** خصم العرض لسطر واحد (بالحبة الأساسية) */
    static double discountFor(Offer o, InvLine l) {
        double pieces = l.qty * l.factor, piecePrice = l.factor == 0 ? l.price : l.price / l.factor;
        switch (o.kind) {
            case Offer.PERCENT: return Math.max(0, l.qty * l.price * Math.min(100, o.value) / 100.0);
            case Offer.SPECIAL: return Math.max(0, (piecePrice - o.value) * pieces);
            default: {
                if (o.qty <= 0) return 0;
                double bundles = Math.floor(pieces / o.qty + 1e-9);
                return Math.max(0, bundles * (o.qty * piecePrice - o.value));
            }
        }
    }

    /** يطبّق العروض على أسطر فاتورة البيع. يرجع true إن تغيّر شيء */
    public static boolean apply(List<InvLine> lines) {
        boolean changed = false; LocalDate today = LocalDate.now();
        for (InvLine l : lines) {
            if (l.manualDisc) continue;
            Offer o = live(l.productId, today);
            double d = o == null || l.qty <= 0 ? 0 : Ui.r(discountFor(o, l));
            if (d > 0 && Ops.noLoss() && l.cost > 0) d = Math.min(d, Ops.maxLineDiscount(l)); // العرض لا يجعل البيع بخسارة
            String name = d > 0 ? title(o) : "";
            if (l.offer == null) l.offer = "";
            if (d > 0 || !l.offer.isEmpty()) {
                if (Math.abs(l.discount - d) > 0.0005 || !name.equals(l.offer)) { l.discount = d; l.offer = name; changed = true; }
            }
        }
        return changed;
    }

    /** نص «عرض» لاقتراحات الأصناف وشاشة السعر */
    public static String hint(Product p) { Offer o = live(p.id, LocalDate.now()); return o == null ? "" : "عرض: " + title(o); }
    public static Offer byId(long id) { for (Offer o : Store.db.offers) if (o.id == id) return o; return null; }
}
