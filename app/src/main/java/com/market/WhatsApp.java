package com.market;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** إرسال رسائل واتساب جاهزة (يفتح واتساب ويب أو تطبيق واتساب على الجهاز) */
public final class WhatsApp {
    private WhatsApp() {}

    /** 0791234567 ← 962791234567 */
    public static String normalize(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("[^0-9+]", "");
        if (d.startsWith("+")) d = d.substring(1);
        if (d.startsWith("00")) d = d.substring(2);
        String cc = Store.db.get("waCountry", "962");
        if (d.startsWith("0")) d = cc + d.substring(1);
        else if (d.length() == 9 && d.startsWith("7")) d = cc + d;
        return d;
    }
    public static String link(String phone, String text) {
        String n = normalize(phone);
        return "https://wa.me/" + n + "?text=" + enc(text).replace("+", "%20");
    }
    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
    static String header() { return "*" + Store.db.get("storeName", "") + "*" + (Store.db.get("phone", "").isEmpty() ? "" : "\nهاتف: " + Store.db.get("phone", "")); }

    public static String invoiceText(Model.Invoice inv) {
        StringBuilder b = new StringBuilder(header()).append("\n\n").append(inv.type.title).append(" رقم ").append(inv.no).append("\nالتاريخ: ").append(Ui.dt(inv.date)).append("\n");
        for (Model.InvLine l : inv.lines) b.append("• ").append(l.name).append(" × ").append(Ui.q(l.qty)).append(" = ").append(Ui.m(l.total())).append("\n");
        if (inv.discount > 0) b.append("الخصم: ").append(Ui.m(inv.discount)).append("\n");
        b.append("*الإجمالي: ").append(Ui.m(inv.total)).append(" ").append(Store.db.get("currency", "")).append("*\n");
        if (inv.total - inv.paid > 0.0005) b.append("المتبقي (آجل): ").append(Ui.m(inv.total - inv.paid)).append("\n");
        if (inv.partyId != 0) { Model.Party p = Store.db.party(inv.partyId); if (p != null) b.append("رصيد حسابك: ").append(Ui.m(Ops.balance(p))).append("\n");
            if (inv.type == Model.InvType.SALE && Loyalty.enabled()) b.append("نقاطك: ").append(Loyalty.balance(inv.partyId)).append("\n"); }
        return b.append("\n").append(Store.db.get("footer", "شكراً لتسوقكم معنا").split("\\r?\\n")[0]).toString();
    }
    public static String balanceText(Model.Party p) {
        double bal = Ops.balance(p);
        return header() + "\n\nمرحباً " + p.name + "،\n" + (p.kind == Model.CUSTOMER
                ? (bal > 0.0005 ? "نود تذكيركم بأن رصيد حسابكم المستحق هو *" + Ui.m(bal) + " " + Store.db.get("currency", "") + "*.\nنرجو التكرم بالسداد في أقرب وقت. شكراً لكم." : "رصيد حسابكم: " + Ui.m(bal) + ". شكراً لتعاملكم معنا.")
                : "رصيد حسابكم لدينا: *" + Ui.m(bal) + " " + Store.db.get("currency", "") + "*.");
    }
}
