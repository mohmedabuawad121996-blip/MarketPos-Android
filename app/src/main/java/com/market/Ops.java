package com.market;

import com.market.Model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** منطق العمليات المحاسبية: كل عملية تكتب حركات المخزون والصندوق والحسابات وتُعكس عند الإلغاء */
public final class Ops {
    private Ops() {}
    static Store db() { return Store.db; }

    public static class OpException extends RuntimeException { public OpException(String m) { super(m); } }

    // ===================== حركات أساسية =====================
    static void cash(long wallet, double amount, String ref, long refId, String notes, LocalDateTime date) {
        if (Math.abs(amount) < 0.0005) return;
        CashMove c = new CashMove(); c.id = db().next("cash"); c.walletId = wallet == 0 ? db().defaultWallet() : wallet;
        c.amount = Ui.r(amount); c.ref = ref; c.refId = refId; c.notes = notes; c.date = date; db().cash.add(c);
    }
    static void stock(Product p, double qty, double cost, String ref, long refId, String notes, LocalDateTime date) { stock(p, qty, cost, ref, refId, notes, date, 0); }
    /** حركة مخزون في مستودع محدد (0 = الرئيسي). p.qty يبقى إجمالي كل المستودعات */
    static void stock(Product p, double qty, double cost, String ref, long refId, String notes, LocalDateTime date, long wh) {
        if (Math.abs(qty) < 1e-9) return;
        StockMove s = new StockMove(); s.id = db().next("stock"); s.productId = p.id; s.qty = qty; s.cost = cost;
        s.ref = ref; s.refId = refId; s.notes = notes; s.date = date; s.warehouseId = db().whOf(wh); db().stock.add(s);
        db().whAdd(s);
        if (!"TRANSFER".equals(ref)) p.qty = Ui.r(p.qty + qty);
    }

    /** إنشاء المستودع الرئيسي إن لم يوجد */
    public static void ensureWarehouses() {
        if (!db().warehouses.isEmpty()) return;
        Warehouse w = new Warehouse(); w.id = db().next("warehouse"); w.name = "المستودع الرئيسي"; db().warehouses.add(w);
        Store.save();
    }

    /** تحويل بضاعة بين مستودعين */
    public static Transfer transfer(long from, long to, Map<Long, Double> items, String notes) {
        if (from == to) throw new OpException("اختر مستودعين مختلفين");
        if (items.isEmpty()) throw new OpException("أضف أصنافاً للتحويل");
        List<String> short_ = new ArrayList<>();
        for (Map.Entry<Long, Double> e : items.entrySet()) { if (e.getValue() <= 0) throw new OpException("كمية غير صحيحة");
            Product p = db().products.get(e.getKey()); double av = db().whQty(e.getKey(), from);
            if (p != null && av + 1e-9 < e.getValue()) short_.add(p.name + " (المتوفر " + Ui.q(av) + ")"); }
        if (!short_.isEmpty() && !db().getB("allowNegative", true)) throw new OpException("الكمية غير كافية في المستودع:\n" + String.join("\n", short_));
        Transfer t = new Transfer(); t.id = db().next("transfer"); t.no = db().next("no_TRANSFER"); t.fromWh = from; t.toWh = to; t.notes = notes == null ? "" : notes; t.user = Session.name();
        StringBuilder b = new StringBuilder();
        String note = "تحويل رقم " + t.no + ": " + db().whName(from) + " ← " + db().whName(to);
        for (Map.Entry<Long, Double> e : items.entrySet()) { Product p = db().products.get(e.getKey()); if (p == null) continue;
            stock(p, -e.getValue(), p.cost, "TRANSFER", t.id, note, t.date, from);
            stock(p, e.getValue(), p.cost, "TRANSFER", t.id, note, t.date, to);
            b.append(p.id).append(':').append(e.getValue()).append(';'); }
        t.items = b.toString(); db().transfers.add(t); Store.save(); return t;
    }
    public static void cancel(Transfer t) {
        if (t.cancelled) return;
        if (!db().getB("allowNegative", true)) {
            List<String> short_ = new ArrayList<>();
            for (String it : t.items.split(";")) { if (it.isEmpty()) continue; String[] a = it.split(":"); Product p = db().products.get(Long.parseLong(a[0])); double q = Double.parseDouble(a[1]); if (p == null) continue;
                double av = db().whQty(p.id, t.toWh); if (av + 1e-9 < q) short_.add(p.name + " (المتوفر " + Ui.q(av) + ")"); }
            if (!short_.isEmpty()) throw new OpException("لا يمكن إلغاء التحويل لأن البضاعة المحوّلة لم تعد كافية في المستودع المستلم:\n" + String.join("\n", short_));
        }
        String note = "إلغاء تحويل رقم " + t.no;
        for (String it : t.items.split(";")) { if (it.isEmpty()) continue; String[] a = it.split(":"); Product p = db().products.get(Long.parseLong(a[0])); double q = Double.parseDouble(a[1]); if (p == null) continue;
            stock(p, -q, p.cost, "TRANSFER", t.id, note, LocalDateTime.now(), t.toWh);
            stock(p, q, p.cost, "TRANSFER", t.id, note, LocalDateTime.now(), t.fromWh); }
        t.cancelled = true; Store.save();
    }
    static void ledger(int kind, long party, double debit, double credit, String ref, long refId, String notes, LocalDateTime date) {
        if (party == 0) return;
        Ledger l = new Ledger(); l.id = db().next("ledger"); l.partyKind = kind; l.partyId = party;
        l.debit = Ui.r(debit); l.credit = Ui.r(credit); l.ref = ref; l.refId = refId; l.notes = notes; l.date = date; db().ledger.add(l);
    }

    // ===================== الأرصدة =====================
    public static double balance(Party p) {
        double d = 0, c = 0;
        for (Ledger l : db().ledger) if (l.partyId == p.id && l.partyKind == p.kind) { d += l.debit; c += l.credit; }
        return Ui.r(p.kind == Model.CUSTOMER ? d - c : c - d);
    }
    public static Map<Long, Double> allBalances(int kind) {
        Map<Long, Double> m = new HashMap<>();
        for (Ledger l : db().ledger) if (l.partyKind == kind) m.merge(l.partyId, kind == Model.CUSTOMER ? l.debit - l.credit : l.credit - l.debit, Double::sum);
        m.replaceAll((k, v) -> Ui.r(v));
        return m;
    }
    public static double walletBalance(long wallet) {
        double s = 0; for (CashMove c : db().cash) if (c.walletId == wallet) s += c.amount; return Ui.r(s);
    }
    public static double invoicePaidLater(Invoice inv) {
        double s = 0; for (Voucher v : db().vouchers) if (!v.cancelled && v.invoiceId == inv.id) s += v.amount; return s;
    }
    /** المتبقي من الفاتورة بحسب ما دُفع عليها مباشرة (نقداً أو بسندات مرتبطة بها) */
    public static double rawRemaining(Invoice inv) { return Ui.r(inv.total - inv.paid - invoicePaidLater(inv)); }

    private static long remRev = -1; private static Map<Long, Double> remMap = new HashMap<>(); private static Store remDb;
    /**
     * المتبقي الفعلي من الفاتورة الآجلة: الدفعات العامة غير المرتبطة بفاتورة (سندات القبض، المرتجعات، الرصيد الافتتاحي الدائن)
     * توزَّع على أقدم الفواتير أولاً، فيبقى مجموع المتبقي مطابقاً لرصيد الحساب.
     */
    public static synchronized double remaining(Invoice inv) {
        double raw = rawRemaining(inv);
        if (inv.cancelled || inv.partyId == 0 || (inv.type != InvType.SALE && inv.type != InvType.PURCHASE) || raw <= 0.0005) return Math.max(0, raw);
        if (remRev != Store.rev || remDb != db()) { remMap = allocate(); remRev = Store.rev; remDb = db(); }
        Double v = remMap.get(inv.id); return v == null ? raw : v;
    }
    static Map<Long, Double> allocate() {
        Map<Long, Double> linked = new HashMap<>();
        for (Voucher v : db().vouchers) if (!v.cancelled && v.invoiceId != 0) linked.merge(v.invoiceId, v.amount, Double::sum);
        Map<String, List<Invoice>> open = new HashMap<>(); Map<String, Double> sum = new HashMap<>();
        for (Invoice i : db().invoices) { if (i.cancelled || i.partyId == 0 || (i.type != InvType.SALE && i.type != InvType.PURCHASE)) continue;
            double r = Ui.r(i.total - i.paid - linked.getOrDefault(i.id, 0.0)); if (r <= 0.0005) continue;
            String k = (i.type == InvType.SALE ? Model.CUSTOMER : Model.SUPPLIER) + ":" + i.partyId;
            open.computeIfAbsent(k, x -> new ArrayList<>()).add(i); sum.merge(k, r, Double::sum); }
        Map<Long, Double> cb = allBalances(Model.CUSTOMER), sb = allBalances(Model.SUPPLIER);
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<String, List<Invoice>> e : open.entrySet()) {
            String[] k = e.getKey().split(":"); long pid = Long.parseLong(k[1]);
            double bal = (Integer.parseInt(k[0]) == Model.CUSTOMER ? cb : sb).getOrDefault(pid, 0.0);
            double excess = Ui.r(sum.get(e.getKey()) - Math.max(0, bal), 3);
            List<Invoice> l = e.getValue(); l.sort(Comparator.comparing(i -> i.date));
            for (Invoice i : l) { double r = Ui.r(i.total - i.paid - linked.getOrDefault(i.id, 0.0));
                double take = Math.max(0, Math.min(r, excess)); excess -= take; out.put(i.id, Ui.r(r - take)); }
        }
        return out;
    }

    // ===================== الحساب الضريبي =====================
    /** منع الخصم بخسارة مفعّل؟ (الإعداد: السماح بالبيع أو الخصم بأقل من التكلفة) */
    public static boolean noLoss() { return !db().getB("allowBelowCost", false); }
    /** أقصى خصم يبقي السطر رابحاً (لا ينزل عن التكلفة) */
    public static double maxLineDiscount(InvLine l) { return Math.max(0, Ui.r(l.qty * l.price - l.cost * l.pieces())); }
    /** أقصى خصم على مستوى الفاتورة يبقيها غير خاسرة */
    public static double maxInvoiceDiscount(Invoice inv) { return Math.max(0, Ui.r(inv.subtotal - inv.costTotal())); }

    public static void compute(Invoice inv) {
        boolean taxOn = db().getB("taxEnabled", true);
        double rate = db().getD("taxRate", 16) / 100.0;
        boolean incl = db().getB("taxInclusive", true);
        double sub = 0, taxable = 0;
        for (InvLine l : inv.lines) { sub += l.total(); if (l.taxable) taxable += l.total(); }
        sub = Ui.r(sub);
        double ratio = sub == 0 ? 0 : (sub - inv.discount) / sub;
        double base = taxable * ratio;
        double tax = 0;
        if (taxOn && rate > 0) tax = incl ? base - base / (1 + rate) : base * rate;
        inv.subtotal = sub; inv.tax = Ui.r(tax);
        inv.total = Ui.r(sub - inv.discount + (incl || !taxOn ? 0 : tax));
    }

    // ===================== الفواتير =====================
    public static void validate(Invoice inv) { validate(inv, 0); }

    /** الكمية (بالحبة) التي ما زال يمكن إرجاعها من كل صنف في الفاتورة الأصلية، بعد طرح المرتجعات السابقة. exceptId: مرتجع يُستثنى (عند تعديله) */
    public static Map<Long, Double> returnable(Invoice orig, long exceptId) {
        Map<Long, Double> left = new LinkedHashMap<>();
        for (InvLine l : orig.lines) left.merge(l.productId, l.pieces(), Double::sum);
        InvType rt = orig.type == InvType.SALE ? InvType.SALE_RETURN : InvType.PURCHASE_RETURN;
        for (Invoice r : db().invoices) if (r.type == rt && !r.cancelled && r.refId == orig.id && r.id != exceptId)
            for (InvLine l : r.lines) left.merge(l.productId, -l.pieces(), Double::sum);
        return left;
    }

    /** سطور الفاتورة الأصلية بالكميات المتبقية للإرجاع فقط (لتحميلها في شاشة المرتجع) */
    public static List<InvLine> returnableLines(Invoice orig) {
        Map<Long, Double> left = returnable(orig, 0); List<InvLine> out = new ArrayList<>();
        for (InvLine l : orig.lines) {
            double rem = left.getOrDefault(l.productId, 0.0); if (rem <= 1e-9) continue;
            double take = Math.min(rem, l.pieces()); left.put(l.productId, rem - take);
            InvLine c = l.copy(); double ratio = take / l.pieces(), f = l.factor <= 0 ? 1 : l.factor, units = take / f;
            if (Math.abs(units - Math.rint(units)) < 1e-9) c.qty = Math.rint(units);
            else { // المتبقي ليس عدداً صحيحاً من الوحدة الكبرى: يُحمَّل بالحبة حتى لا يتجاوز التقريب الكمية المسموحة
                Product p = db().products.get(l.productId);
                c.factor = 1; c.qty = take; c.price = l.price / f; if (p != null && p.unit != null && !p.unit.isEmpty()) c.unit = p.unit;
            }
            c.discount = Ui.r(l.discount * ratio, 3); out.add(c);
        }
        return out;
    }

    /** فحص فوري لشاشة المرتجع: نص الخطأ إن تجاوزت السطور المسموح، وإلا null */
    public static String returnError(InvType type, long refId, List<InvLine> lines, long selfId) {
        Invoice t = new Invoice(); t.type = type; t.refId = refId; t.id = selfId; t.lines = lines;
        try { checkReturn(t); return null; } catch (OpException e) { return e.getMessage(); }
    }

    /** المرتجع يُربط بفاتورته الأصلية ولا يتجاوز كمياتها، إلا إذا فُعّل «السماح بمرتجع حر» في الإعدادات */
    /** فاتورة المشتريات التي استُلمت بها الطلبية (غير ملغاة)، أو null إن لم تُستلم بعد. exceptId: فاتورة تُستثنى عند تعديلها */
    public static Invoice receivedBy(Invoice order, long exceptId) {
        if (order == null || order.type != InvType.ORDER) return null;
        for (Invoice i : db().invoices) if (i.type == InvType.PURCHASE && !i.cancelled && i.refId == order.id && i.id != exceptId) return i;
        return null;
    }

    /** الطلبية تُستلم مرة واحدة فقط: لا تُحفظ فاتورة مشتريات ثانية من الطلبية نفسها، ولا من طلبية ملغاة */
    static void checkOrderReceipt(Invoice inv) {
        if (inv.type != InvType.PURCHASE || inv.refId == 0) return;
        Invoice o = db().invoice(inv.refId); if (o == null || o.type != InvType.ORDER) return;
        if (o.cancelled) throw new OpException("الطلبية رقم " + o.no + " ملغاة ولا يمكن استلامها");
        Invoice prev = receivedBy(o, inv.id);
        if (prev != null) throw new OpException("الطلبية رقم " + o.no + " استُلمت من قبل بفاتورة المشتريات رقم " + prev.no);
    }

    static void checkReturn(Invoice inv) {
        if (inv.type != InvType.SALE_RETURN && inv.type != InvType.PURCHASE_RETURN) return;
        if (db().getB("allowFreeReturn", false)) return;
        InvType ot = inv.type == InvType.SALE_RETURN ? InvType.SALE : InvType.PURCHASE;
        if (inv.refId == 0) throw new OpException("المرتجع يجب أن يُربط بـ" + ot.title + " الأصلية.\nاستخدم زر «استيراد من فاتورة» وأدخل رقمها.");
        Invoice o = db().invoice(inv.refId);
        if (o == null || o.type != ot || o.cancelled) throw new OpException("الفاتورة الأصلية للمرتجع غير موجودة أو ملغاة");
        Map<Long, Double> left = returnable(o, inv.id), want = new LinkedHashMap<>(); Map<Long, String> names = new HashMap<>();
        for (InvLine l : inv.lines) { want.merge(l.productId, l.pieces(), Double::sum); names.put(l.productId, l.name); }
        for (Map.Entry<Long, Double> e : want.entrySet()) {
            Double rem = left.get(e.getKey());
            if (rem == null) throw new OpException("الصنف «" + names.get(e.getKey()) + "» غير موجود في الفاتورة الأصلية رقم " + o.no);
            if (e.getValue() > rem + 1e-6) throw new OpException("كمية المرتجع للصنف «" + names.get(e.getKey()) + "» (" + Ui.q(e.getValue()) + ") أكبر من المتبقي للإرجاع من الفاتورة رقم " + o.no + " (" + Ui.q(Math.max(0, rem)) + ")");
        }
    }
    /** creditRelief: عند تعديل فاتورة آجلة يُطرح أثر النسخة القديمة من رصيد العميل قبل فحص سقف الائتمان */
    public static void validate(Invoice inv, double creditRelief) {
        if (inv.lines.isEmpty()) throw new OpException("الفاتورة فارغة");
        if (!inv.type.noEffect() && !License.canOperate()) throw new OpException("انتهت الفترة التجريبية. يرجى تفعيل البرنامج من الإعدادات");
        for (InvLine l : inv.lines) {
            if (l.qty <= 0) throw new OpException("كمية غير صحيحة للصنف: " + l.name);
            if (l.price < 0) throw new OpException("سعر غير صحيح للصنف: " + l.name);
        }
        checkReturn(inv); checkOrderReceipt(inv);
        if (inv.type == InvType.SALE && !db().getB("allowBelowCost", false)) {
            for (InvLine l : inv.lines) {
                double unitCost = l.cost * l.factor;
                double net = l.qty == 0 ? 0 : l.total() / l.qty;
                if (unitCost > 0 && net + 0.0005 < unitCost)
                    throw new OpException("لا يمكن البيع أو الخصم بأقل من سعر التكلفة\n" + l.name + "\nالتكلفة: " + Ui.m(unitCost) + "  سعر البيع بعد الخصم: " + Ui.m(net)
                            + "\nأقصى خصم مسموح لهذا السطر: " + Ui.m(maxLineDiscount(l)));
            }
            double cost = inv.costTotal();
            if (inv.discount > 0 && cost > 0 && inv.subtotal - inv.discount + 0.0005 < cost)
                throw new OpException("الخصم على الفاتورة يجعل البيع بخسارة\nأقصى خصم مسموح على هذه الفاتورة: " + Ui.m(maxInvoiceDiscount(inv)));
        }
        if (inv.discount < -0.0005) throw new OpException("قيمة الخصم غير صحيحة");
        if (inv.discount > inv.subtotal + 0.0005) throw new OpException("الخصم أكبر من مجموع الفاتورة");
        if (inv.paid < 0) throw new OpException("المبلغ المدفوع غير صحيح");
        if (!inv.type.noEffect() && inv.partyId == 0 && Math.abs(inv.paid - inv.total) > 0.0005)
            throw new OpException("البيع/الشراء الآجل يتطلب اختيار " + (inv.type.customerSide() ? "عميل" : "مورد"));
        if (inv.paid - inv.total > 0.0005) throw new OpException("المدفوع أكبر من قيمة الفاتورة");
        if (inv.type == InvType.SALE && inv.partyId != 0) {
            Party p = db().party(inv.partyId);
            if (p != null && p.creditLimit > 0 && balance(p) - creditRelief + inv.total - inv.paid > p.creditLimit + 0.0005)
                throw new OpException("تجاوز سقف الائتمان للعميل " + p.name + " (" + Ui.m(p.creditLimit) + ")");
        }
    }

    /** أسماء الأصناف التي ستصبح كميتها سالبة */
    public static List<String> negativeStock(Invoice inv) {
        List<String> r = new ArrayList<>();
        if (inv.type.stockSign() >= 0) return r;
        Map<Long, Double> need = new HashMap<>();
        for (InvLine l : inv.lines) need.merge(l.productId, l.pieces(), Double::sum);
        boolean multi = db().multiWh();
        for (Map.Entry<Long, Double> e : need.entrySet()) { Product p = db().products.get(e.getKey()); if (p == null) continue;
            double av = multi ? db().whQty(p.id, inv.warehouseId) : p.qty;
            if (av - e.getValue() < -1e-9) r.add(p.name + " (المتوفر" + (multi ? " في " + db().whName(inv.warehouseId) : "") + " " + Ui.q(av) + ")"); }
        return r;
    }

    public static Invoice save(Invoice inv) {
        compute(inv);
        validate(inv);
        Loyalty.check(inv);
        if (inv.type == InvType.SALE && !db().getB("allowNegative", true) && !negativeStock(inv).isEmpty())
            throw new OpException("الكمية غير متوفرة:\n" + String.join("\n", negativeStock(inv)));
        inv.id = db().next("invoice");
        inv.no = db().next("no_" + inv.type.name());
        if (inv.walletId == 0) inv.walletId = db().defaultWallet();
        inv.partyName = db().partyName(inv.partyId);
        inv.user = Session.name();
        apply(inv, 1);
        db().invoices.add(inv);
        Store.save();
        return inv;
    }

    private static void apply(Invoice inv, int sign) { apply(inv, sign, false); }
    /** restore=true: استعادة فاتورة بعد فشل التعديل، فلا تُعاد حساب التكلفة ولا تُضاف دفعات صلاحية */
    private static void apply(Invoice inv, int sign, boolean restore) {
        if (inv.type.noEffect()) return;
        String ref = inv.type.name(); String note = inv.type.title + " رقم " + inv.no + (sign < 0 ? " (إلغاء)" : "");
        LocalDateTime d = sign > 0 ? inv.date : LocalDateTime.now();
        int ss = inv.type.stockSign();
        // قاعدة التكلفة = نفس قاعدة حساب المخزون في الدفتر (صافي الفاتورة بدون الضريبة وبعد خصم الفاتورة)
        double lineSum = 0; for (InvLine l : inv.lines) lineSum += l.total();
        double basisFactor = lineSum <= 0 ? 1 : Ui.r(inv.total - inv.tax, 3) / lineSum;
        for (InvLine l : inv.lines) {
            Product p = db().products.get(l.productId);
            if (p == null) continue;
            if (inv.type == InvType.PURCHASE && sign > 0 && !restore) updateCost(p, l, basisFactor);
            if (inv.type == InvType.PURCHASE && sign < 0) revertCost(p, l, basisFactor);
            // أي حركة أخرى تدخل أو تخرج بتكلفة تختلف عن المتوسط الحالي (إلغاء/تعديل بيع، مرتجعات) تُعيد حساب المتوسط،
            // حتى تبقى قيمة المخزون مطابقة للدفتر المحاسبي
            if (inv.type != InvType.PURCHASE && !restore) reavg(p, sign * ss * l.pieces(), l.cost);
            stock(p, sign * ss * l.pieces(), l.cost, ref, inv.id, note, d, inv.warehouseId);
            if (inv.type == InvType.PURCHASE && sign > 0 && !restore && l.expiry != null && !l.expiry.trim().isEmpty()) Expiry.addBatch(p, l.pieces(), l.expiry, inv.id, inv.warehouseId, inv.date);
        }
        if (inv.type == InvType.PURCHASE && sign < 0) for (Batch b : db().batches) if (b.invoiceId == inv.id) b.closed = true;
        double second = Math.min(inv.paid2, inv.paid);
        cash(inv.walletId, sign * inv.type.cashSign() * (inv.paid - second), ref, inv.id, note, d);
        if (second > 0.0005) cash(inv.wallet2 == 0 ? inv.walletId : inv.wallet2, sign * inv.type.cashSign() * second, ref, inv.id, note, d);
        int kind = inv.type.customerSide() ? Model.CUSTOMER : Model.SUPPLIER;
        double t = inv.total, pd = inv.paid;
        switch (inv.type) {
            case SALE: case PURCHASE_RETURN: ledger(kind, inv.partyId, sign > 0 ? t : pd, sign > 0 ? pd : t, ref, inv.id, note, d); break;
            case PURCHASE: case SALE_RETURN: ledger(kind, inv.partyId, sign > 0 ? pd : t, sign > 0 ? t : pd, ref, inv.id, note, d); break;
            default:
        }
    }

    /** تكلفة القطعة = صافي قيمة السطر بعد خصم الفاتورة وبدون الضريبة، مقسوماً على عدد القطع */
    private static void updateCost(Product p, InvLine l, double basisFactor) {
        double lineNet = l.total() * basisFactor;
        double newCost = l.pieces() == 0 ? 0 : lineNet / l.pieces();
        if (db().get("costMethod", "avg").equals("avg") && p.qty > 0) {
            double totalQty = p.qty + l.pieces();
            newCost = (p.qty * p.cost + l.pieces() * newCost) / totalQty;
        }
        p.cost = Ui.r(newCost, 4);
        l.cost = p.cost;
    }

    /** عكس أثر فاتورة الشراء على متوسط التكلفة عند إلغائها أو تعديلها، حتى لا يُحتسب الشراء نفسه مرتين في المتوسط */
    private static void revertCost(Product p, InvLine l, double basisFactor) {
        if (!db().get("costMethod", "avg").equals("avg") || l.pieces() <= 0) return;
        double unit = l.total() * basisFactor / l.pieces();
        double rest = p.qty - l.pieces();
        if (rest <= 1e-9) return; // لا كمية سابقة يُحسب متوسطها: تبقى التكلفة كما هي
        double c = (p.qty * p.cost - l.pieces() * unit) / rest;
        if (c > 0) p.cost = Ui.r(c, 4);
    }

    /** متوسط مرجّح عند حركة بتكلفة مختلفة عن المتوسط الحالي. لا يغيّر شيئاً إن كانت التكلفة نفسها */
    static void reavg(Product p, double delta, double unit) {
        if (!db().get("costMethod", "avg").equals("avg") || unit <= 0 || Math.abs(delta) < 1e-9 || Math.abs(unit - p.cost) < 1e-9) return;
        if (p.qty <= 1e-9) { if (delta > 0) p.cost = Ui.r(unit, 4); return; }
        double nq = p.qty + delta; if (nq <= 1e-9) return;
        double c = (p.qty * p.cost + delta * unit) / nq;
        if (c > 0) p.cost = Ui.r(c, 4);
    }

    public static void cancel(Invoice inv) {
        if (JoFotara.submitted(inv)) throw new OpException("هذه الفاتورة مقبولة في نظام الفوترة الوطني ولا يمكن إلغاؤها.\nأصدر مرتجع مبيعات لها بدلاً من الإلغاء.");
        if (inv.cancelled) throw new OpException("الفاتورة ملغاة مسبقاً");
        if (!inv.type.noEffect() && invoicePaidLater(inv) > 0)
            throw new OpException("يوجد سندات مرتبطة بهذه الفاتورة، ألغِ السندات أولاً");
        apply(inv, -1);
        inv.cancelled = true;
        Store.save();
    }

    /** تعديل فاتورة في مكانها: تبقى الفاتورة نفسها برقمها ومعرّفها (لا تُلغى ولا تُنشأ فاتورة جديدة).
     *  يُعكس أثر النسخة الأصلية، ثم يُطبَّق أثر النسخة المعدّلة على المخزون والصندوق والحساب */
    public static Invoice replace(Invoice old, Invoice neu) {
        if (old.cancelled) throw new OpException("الفاتورة ملغاة، لا يمكن تعديلها");
        if (!old.type.noEffect() && invoicePaidLater(old) > 0)
            throw new OpException("يوجد سندات مرتبطة بهذه الفاتورة، ألغِ السندات أولاً");
        compute(neu); neu.id = old.id; // المعرّف مبكراً ليُستثنى المرتجع نفسه من فحص الكميات عند تعديله
        validate(neu, old.type == InvType.SALE && old.partyId != 0 && old.partyId == neu.partyId ? old.total - old.paid : 0);
        // لقطة قبل التعديل: تكلفة الأصناف ودفعات الصلاحية المفتوحة، لاستعادتها بدقة عند الفشل
        Map<Long, Double> costBefore = new HashMap<>();
        for (InvLine l : old.lines) { Product p = db().products.get(l.productId); if (p != null) costBefore.putIfAbsent(p.id, p.cost); }
        List<Batch> openBatches = new ArrayList<>();
        for (Batch b : db().batches) if (b.invoiceId == old.id && !b.closed) openBatches.add(b);
        apply(old, -1); // عكس أثر النسخة الأصلية فقط (دون وضع علامة الإلغاء على الفاتورة)
        try {
            Loyalty.check(neu);
            if (neu.type == InvType.SALE && !db().getB("allowNegative", true) && !negativeStock(neu).isEmpty())
                throw new OpException("الكمية غير متوفرة:\n" + String.join("\n", negativeStock(neu)));
            neu.id = old.id; neu.no = old.no; neu.refId = old.refId;
            if (neu.walletId == 0) neu.walletId = db().defaultWallet();
            neu.partyName = db().partyName(neu.partyId);
            neu.user = Session.name();
            apply(neu, 1);
            List<Invoice> all = db().invoices;
            boolean placed = false;
            for (int i = 0; i < all.size(); i++) if (all.get(i).id == old.id) { all.set(i, neu); placed = true; break; }
            if (!placed) all.add(neu);
            Store.save();
            return neu;
        }
        catch (RuntimeException ex) { restoreAfterFailedReplace(old, costBefore, openBatches); throw ex; }
    }
    /** إن فشل حفظ البديل (مثلاً: الكمية أو سقف الائتمان أو النقاط بعد الإلغاء) تُعاد الفاتورة الأصلية كما كانت:
     *  الحركات تُعاد دون تغيير التكلفة أو إضافة دفعات صلاحية جديدة، ثم تُستعاد التكلفة والدفعات المفتوحة من اللقطة */
    private static void restoreAfterFailedReplace(Invoice old, Map<Long, Double> costBefore, List<Batch> openBatches) {
        old.cancelled = false;
        apply(old, 1, true);
        for (Batch b : openBatches) b.closed = false;
        for (Map.Entry<Long, Double> e : costBefore.entrySet()) { Product p = db().products.get(e.getKey()); if (p != null) p.cost = e.getValue(); }
        Store.save();
    }

    // ===================== السندات =====================
    public static Voucher voucher(boolean in, int kind, long party, double amount, long wallet, long invoiceId, String notes, LocalDateTime date) {
        if (amount <= 0) throw new OpException("أدخل مبلغاً صحيحاً");
        if (!License.canOperate()) throw new OpException("انتهت الفترة التجريبية. يرجى تفعيل البرنامج");
        if (invoiceId != 0) { Invoice inv = db().invoice(invoiceId);
            if (inv != null && (inv.partyId != party || inv.type != (in ? InvType.SALE : InvType.PURCHASE)))
                throw new OpException("هذا السند لا يطابق الفاتورة رقم " + inv.no + ": سند القبض يُربط بفاتورة بيع لنفس العميل، وسند الصرف بفاتورة شراء لنفس المورد");
            if (inv != null && Ui.r(amount, 3) > rawRemaining(inv) + 0.0005)
                throw new OpException("المبلغ أكبر من المتبقي من الفاتورة رقم " + inv.no + " (" + Ui.m(Math.max(0, rawRemaining(inv))) + ")\nسجّل الزيادة كسند عام بدون اختيار فاتورة."); }
        if (!in && walletBalance(wallet) < amount && !db().getB("allowNegativeCash", false)
                && !Ui.confirm("رصيد المحفظة غير كافٍ (" + Ui.m(walletBalance(wallet)) + "). متابعة؟")) throw new OpException("تم الإلغاء");
        Voucher v = new Voucher(); v.id = db().next("voucher"); v.no = db().next(in ? "no_RECEIPT" : "no_PAYMENT");
        v.in = in; v.partyKind = kind; v.partyId = party; v.amount = Ui.r(amount); v.walletId = wallet; v.invoiceId = invoiceId; v.user = Session.name();
        v.notes = notes; v.date = date;
        String ref = in ? "RECEIPT" : "PAYMENT"; String note = (in ? "سند قبض" : "سند صرف") + " رقم " + v.no + (notes.trim().isEmpty() ? "" : " - " + notes);
        cash(wallet, in ? amount : -amount, ref, v.id, note, date);
        ledger(kind, party, in ? 0 : amount, in ? amount : 0, ref, v.id, note, date);
        db().vouchers.add(v); Store.save();
        return v;
    }

    public static void cancel(Voucher v) {
        if (v.cancelled) throw new OpException("السند ملغى مسبقاً");
        // الإلغاء الكامل: تُحذف قيود السند من الصندوق ودفتر الحساب فيختفي كلياً من الكشوفات والأرصدة
        String ref = v.in ? "RECEIPT" : "PAYMENT";
        db().ledger.removeIf(l -> l.refId == v.id && ref.equals(l.ref));
        db().cash.removeIf(c -> c.refId == v.id && ref.equals(c.ref));
        v.cancelled = true; Store.save();
    }

    public static void setOpening(Party p, double amount) { setOpeningNoPersist(p, amount); Store.save(); }
    static void setOpeningNoPersist(Party p, double amount) {
        db().ledger.removeIf(l -> l.partyId == p.id && l.partyKind == p.kind && l.ref.equals("OPEN"));
        p.opening = Ui.r(amount);
        boolean owedToUs = p.kind == Model.CUSTOMER;
        double a = Math.abs(amount);
        boolean debit = (amount >= 0) == owedToUs;
        Ledger l = new Ledger(); l.id = db().next("ledger"); l.partyKind = p.kind; l.partyId = p.id;
        l.debit = debit ? a : 0; l.credit = debit ? 0 : a; l.ref = "OPEN"; l.notes = "رصيد افتتاحي";
        l.date = p.created == null ? LocalDateTime.now() : p.created;
        if (a > 0) db().ledger.add(l);
        
    }

    // ===================== المصروفات والصندوق =====================
    public static Expense expense(long cat, double amount, long wallet, String notes, LocalDateTime date) {
        if (amount <= 0) throw new OpException("أدخل مبلغاً صحيحاً");
        if (!License.canOperate()) throw new OpException("انتهت الفترة التجريبية. يرجى تفعيل البرنامج");
        Expense e = new Expense(); e.id = db().next("expense"); e.catId = cat; e.amount = Ui.r(amount); e.walletId = wallet; e.notes = notes; e.date = date; e.user = Session.name();
        ExpenseCat c = db().expenseCat(cat);
        cash(wallet, -amount, "EXPENSE", e.id, "مصروف: " + (c == null ? "" : c.name) + (notes.trim().isEmpty() ? "" : " - " + notes), date);
        db().expenses.add(e); Store.save(); return e;
    }
    public static void cancel(Expense e) {
        if (e.cancelled) throw new OpException("المصروف ملغى مسبقاً");
        cash(e.walletId, e.amount, "EXPENSE", e.id, "إلغاء مصروف رقم " + e.id, LocalDateTime.now());
        e.cancelled = true; Store.save();
    }
    public static void cashManual(long wallet, double amount, String notes) {
        if (Math.abs(amount) < 0.0005) throw new OpException("أدخل مبلغاً صحيحاً");
        if (!License.canOperate()) throw new OpException("انتهت الفترة التجريبية. يرجى تفعيل البرنامج");
        cash(wallet, amount, amount > 0 ? "DEPOSIT" : "WITHDRAW", 0, notes, LocalDateTime.now()); Store.save();
    }
    public static void transfer(long from, long to, double amount, String notes) {
        if (from == to) throw new OpException("اختر محفظتين مختلفتين");
        if (amount <= 0) throw new OpException("أدخل مبلغاً صحيحاً");
        if (!License.canOperate()) throw new OpException("انتهت الفترة التجريبية. يرجى تفعيل البرنامج");
        LocalDateTime now = LocalDateTime.now();
        cash(from, -amount, "TRANSFER", 0, "تحويل إلى " + db().walletName(to) + " " + notes, now);
        cash(to, amount, "TRANSFER", 0, "تحويل من " + db().walletName(from) + " " + notes, now);
        Store.save();
    }

    // ===================== المخزون =====================
    public static Product addProduct(Product p, double openingQty) {
        if (p.name.trim().isEmpty()) throw new OpException("اسم الصنف مطلوب");
        if (!p.barcode.trim().isEmpty() && db().byBarcode(p.barcode) != null) throw new OpException("الباركود مستخدم لصنف آخر");
        if (!p.bigCode().isEmpty() && db().byBarcode(p.bigCode()) != null) throw new OpException("باركود الوحدة الكبرى مستخدم لصنف آخر");
        for (String c : p.extraCodes()) if (db().byBarcode(c) != null) throw new OpException("الباركود الإضافي " + c + " مستخدم لصنف آخر");
        p.id = db().next("product"); p.qty = 0;
        db().products.put(p.id, p); db().reindex();
        if (openingQty != 0) stock(p, openingQty, p.cost, "OPEN", 0, "رصيد افتتاحي", LocalDateTime.now());
        Store.save(); return p;
    }
    public static void adjust(Product p, double actual, String notes) { adjust(p, actual, notes, -1); }
    /** تسوية الجرد: wh = -1 يعني إجمالي الصنف (بدون مستودعات) */
    public static void adjust(Product p, double actual, String notes, long wh) {
        double cur = wh < 0 ? p.qty : db().whQty(p.id, wh);
        double diff = Ui.r(actual - cur);
        if (diff == 0) return;
        stock(p, diff, p.cost, "ADJUST", 0, "جرد/تسوية: " + notes + (wh >= 0 && db().multiWh() ? " — " + db().whName(wh) : ""), LocalDateTime.now(), Math.max(0, wh)); Store.save();
    }
    public static Damage damage(Product p, double qty, String reason) { return damage(p, qty, reason, 0); }
    public static Damage damage(Product p, double qty, String reason, long wh) {
        if (qty <= 0) throw new OpException("أدخل كمية صحيحة");
        Damage d = new Damage(); d.id = db().next("damage"); d.productId = p.id; d.qty = qty; d.cost = p.cost; d.reason = reason; d.warehouseId = db().whOf(wh);
        stock(p, -qty, p.cost, "DAMAGE", d.id, "تالف: " + reason, d.date, d.warehouseId);
        db().damages.add(d); Store.save(); return d;
    }
    public static void cancel(Damage d) {
        if (d.cancelled) return;
        Product p = db().products.get(d.productId);
        if (p != null) reavg(p, d.qty, d.cost);
        if (p != null) stock(p, d.qty, d.cost, "DAMAGE", d.id, "إلغاء تالف رقم " + d.id, LocalDateTime.now(), d.warehouseId);
        d.cancelled = true; Store.save();
    }

    // ===================== معالجة البيانات =====================
    /** هل للصنف فواتير أو تالف أو فواتير معلّقة؟ (لا يُحذف حتى لا تتأثر الفواتير السابقة) */
    public static boolean productUsed(long id) {
        for (Invoice i : db().invoices) for (InvLine l : i.lines) if (l.productId == id) return true;
        for (Invoice i : db().held) for (InvLine l : i.lines) if (l.productId == id) return true;
        for (Damage d : db().damages) if (d.productId == id) return true;
        return false;
    }
    /** هل للصنف أي حركة مخزون غير الرصيد الافتتاحي؟ */
    public static boolean productMoved(long id) {
        for (StockMove s : db().stock) if (s.productId == id && !"OPEN".equals(s.ref)) return true;
        return productUsed(id);
    }
    /** حذف أصناف: غير المستخدمة تُحذف نهائياً مع حركاتها وعروضها، والمستخدمة تُوقف (إن طُلب). يرجع {محذوف، موقوف} */
    public static int[] deleteProducts(Collection<Long> ids, boolean deactivateUsed) {
        int del = 0, off = 0; Set<Long> gone = new HashSet<>();
        for (long id : new ArrayList<>(ids)) {
            Product p = db().products.get(id); if (p == null) continue;
            if (productUsed(id)) { if (deactivateUsed && p.active) { p.active = false; off++; } continue; }
            db().products.remove(id); gone.add(id); del++;
        }
        if (!gone.isEmpty()) {
            db().stock.removeIf(s -> gone.contains(s.productId));
            db().offers.removeIf(o -> gone.contains(o.productId));
            db().invalidateWh();
        }
        db().reindex(); Store.save();
        return new int[]{del, off};
    }

    public static int recalcStock() {
        Map<Long, Double> q = new HashMap<>();
        for (StockMove s : db().stock) q.merge(s.productId, s.qty, Double::sum);
        int changed = 0;
        for (Product p : db().products.values()) {
            double v = Ui.r(q.getOrDefault(p.id, 0.0));
            if (Math.abs(v - p.qty) > 1e-9) { p.qty = v; changed++; }
        }
        db().invalidateWh(); Store.save(); return changed;
    }

    public static List<String> integrityCheck() {
        List<String> r = new ArrayList<>();
        Map<Long, Double> q = new HashMap<>();
        for (StockMove s : db().stock) q.merge(s.productId, s.qty, Double::sum);
        for (Product p : db().products.values()) {
            double v = Ui.r(q.getOrDefault(p.id, 0.0));
            if (Math.abs(v - p.qty) > 1e-6) r.add("كمية الصنف لا تطابق الحركات: " + p.name + " (" + Ui.q(p.qty) + " / " + Ui.q(v) + ")");
            if (p.qty < 0) r.add("كمية سالبة: " + p.name + " = " + Ui.q(p.qty));
            if (p.price > 0 && p.cost > p.price) r.add("سعر البيع أقل من التكلفة: " + p.name);
        }
        for (Invoice i : db().invoices) if (i.partyId != 0 && db().party(i.partyId) == null) r.add("فاتورة لطرف محذوف: " + i.type.title + " " + i.no);
        for (Wallet w : db().wallets) { double b = walletBalance(w.id); if (b < 0) r.add("رصيد سالب للمحفظة " + w.name + ": " + Ui.m(b)); }
        if (Store.SQL) { List<String> ic = SqlStore.integrity(); if (!(ic.size() == 1 && ic.get(0).equals("ok"))) for (String x : ic) r.add("قاعدة البيانات: " + x); }
        return r;
    }

    /** حذف كل الحركات مع إبقاء الأصناف والعملاء والموردين والإعدادات */
    public static void wipeTransactions() {
        Store s = db();
        s.invoices.clear(); s.vouchers.clear(); s.expenses.clear(); s.cash.clear(); s.stock.clear(); s.damages.clear();
        s.ledger.removeIf(l -> !l.ref.equals("OPEN"));
        for (Product p : s.products.values()) p.qty = 0;
        s.transfers.clear(); s.journal.clear(); s.batches.clear(); s.invalidateWh();
        for (String k : new ArrayList<>(s.seq.keySet())) if (k.startsWith("no_")) s.seq.remove(k);
        Store.save();
    }

    /** تصفير البرنامج لبداية جديدة: يحذف كل الحركات ويصفّر الكميات مع إبقاء الأصناف والحسابات والإعدادات.
     *  zeroOpening=true يصفّر أيضاً الأرصدة الافتتاحية للعملاء والموردين والمحافظ (بداية صفرية كاملة). */
    public static void resetProgram(boolean zeroOpening) {
        Store s = db();
        s.invoices.clear(); s.vouchers.clear(); s.expenses.clear(); s.stock.clear(); s.damages.clear();
        s.transfers.clear(); s.journal.clear(); s.batches.clear();
        // أرصدة المحافظ المرحّلة (OPEN) تُعامل كأرصدة الحسابات: تبقى إلا إذا طُلب التصفير الكامل
        if (zeroOpening) s.cash.clear(); else s.cash.removeIf(c -> !"OPEN".equals(c.ref));
        for (Product p : s.products.values()) p.qty = 0;
        if (zeroOpening) { s.ledger.clear(); for (Party p : s.parties.values()) p.opening = 0; }
        else s.ledger.removeIf(l -> !l.ref.equals("OPEN"));
        s.invalidateWh();
        for (String k : new ArrayList<>(s.seq.keySet())) if (k.startsWith("no_")) s.seq.remove(k);
        Store.save();
    }

    /** إقفال السنة المالية: أرشفة، ثم ترحيل الأرصدة كأرصدة افتتاحية وحذف حركات السنة */
    public static java.nio.file.Path closeYear() throws Exception {
        java.nio.file.Path archive = Store.backupTo(Store.dataDir.resolve("archive"), "year_" + db().get("fiscalStart", "").replace("-", ""));
        Store s = db();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Double> wb = new HashMap<>(); for (Wallet w : s.wallets) wb.put(w.id, walletBalance(w.id));
        Map<Long, Double> cb = allBalances(Model.CUSTOMER), sb = allBalances(Model.SUPPLIER);
        Map<Long, Map<Long, Double>> wq = new HashMap<>();
        for (Product p : s.products.values()) for (Warehouse w : s.warehouses) { double q = s.whQty(p.id, w.id); if (Math.abs(q) > 1e-9) wq.computeIfAbsent(p.id, k -> new LinkedHashMap<>()).put(w.id, q); }
        s.invoices.removeIf(i -> i.type != InvType.QUOTE || i.cancelled);
        s.transfers.clear(); s.journal.clear();
        // دفعات الصلاحية المفتوحة (بقايا المخزون) تبقى دون ارتباط بفواتير السنة المؤرشفة، والمغلقة تُحذف
        s.batches.removeIf(b -> b.closed);
        for (Batch b : s.batches) b.invoiceId = 0;
        s.vouchers.clear(); s.expenses.clear(); s.cash.clear(); s.stock.clear(); s.ledger.clear(); s.damages.clear();
        for (String k : new ArrayList<>(s.seq.keySet())) if (k.startsWith("no_") && !k.equals("no_QUOTE")) s.seq.remove(k);
        for (Wallet w : s.wallets) cash(w.id, wb.get(w.id), "OPEN", 0, "رصيد مُرحّل من السنة السابقة", now);
        s.invalidateWh();
        for (Product p : s.products.values()) { double q = p.qty; p.qty = 0;
            Map<Long, Double> m = wq.get(p.id);
            if (m == null || s.warehouses.isEmpty()) stock(p, q, p.cost, "OPEN", 0, "رصيد مُرحّل من السنة السابقة", now);
            else { for (Map.Entry<Long, Double> e : m.entrySet()) stock(p, e.getValue(), p.cost, "OPEN", 0, "رصيد مُرحّل من السنة السابقة", now, e.getKey());
                double rest = Ui.r(q - p.qty); if (Math.abs(rest) > 1e-9) stock(p, rest, p.cost, "OPEN", 0, "رصيد مُرحّل من السنة السابقة", now); } }
        for (Party p : s.parties.values()) {
            double b = (p.kind == Model.CUSTOMER ? cb : sb).getOrDefault(p.id, 0.0);
            p.created = now; setOpeningNoSave(p, b);
        }
        s.set("fiscalStart", LocalDate.now().toString());
        Store.saveNow();
        return archive;
    }
    private static void setOpeningNoSave(Party p, double amount) {
        p.opening = amount; if (Math.abs(amount) < 0.0005) return;
        boolean debit = (amount >= 0) == (p.kind == Model.CUSTOMER);
        ledger(p.kind, p.id, debit ? Math.abs(amount) : 0, debit ? 0 : Math.abs(amount), "OPEN", 0, "رصيد افتتاحي مُرحّل", p.created);
    }

    /** إضافة محافظ البطاقة والشيكات للبيانات القديمة (مرة واحدة) */
    public static void ensureWallets() {
        if (db().getB("walletsV3", false)) return;
        boolean card = false, chq = false;
        for (Wallet w : db().wallets) { if ("بطاقة".equals(w.type)) card = true; if ("شيك".equals(w.type)) chq = true; }
        if (!card) { Wallet w = new Wallet(); w.id = db().next("wallet"); w.name = "بطاقة (فيزا / ماستر)"; w.type = "بطاقة"; db().wallets.add(w); }
        if (!chq) { Wallet w = new Wallet(); w.id = db().next("wallet"); w.name = "شيكات"; w.type = "شيك"; db().wallets.add(w); }
        db().setB("walletsV3", true); Store.save();
    }

    public static String refTitle(String ref) {
        switch (ref) {
            case "SALE": return "مبيعات"; case "PURCHASE": return "مشتريات"; case "SALE_RETURN": return "مرتجع مبيعات";
            case "PURCHASE_RETURN": return "مرتجع مشتريات"; case "RECEIPT": return "سند قبض"; case "PAYMENT": return "سند صرف";
            case "EXPENSE": return "مصروف"; case "DEPOSIT": return "إيداع"; case "WITHDRAW": return "سحب"; case "TRANSFER": return "تحويل";
            case "OPEN": return "افتتاحي"; case "ADJUST": return "تسوية جرد"; case "DAMAGE": return "تالف";
            default: return ref;
        }
    }
}
