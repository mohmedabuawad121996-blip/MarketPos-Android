package com.market;

import com.market.Model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * الربط مع نظام الفوترة الوطني الأردني (JoFotara — دائرة ضريبة الدخل والمبيعات).
 * الفاتورة تُبنى بصيغة UBL 2.1 XML، تُرمّز Base64 وتُرسل إلى ISTD مع Client-Id و Secret-Key،
 * ويعود رمز QR يُطبع على الفاتورة.
 */
public final class JoFotara {
    private JoFotara() {}

    public static final String DEFAULT_URL = "https://backend.jofotara.gov.jo/core/invoices/";
    static Store db() { return Store.db; }

    public static boolean enabled() { return db().getB("jofEnabled", false); }
    public static boolean auto() { return db().getB("jofAuto", true); }
    static boolean salesTax() { return !"income".equals(db().get("jofType", "sales")); }
    public static boolean applies(Invoice i) { return i != null && (i.type == InvType.SALE || i.type == InvType.SALE_RETURN); }
    public static boolean submitted(Invoice i) { return i != null && ("SUBMITTED".equals(i.einvStatus) || "ALREADY_SUBMITTED".equals(i.einvStatus)); }

    // ============================================================ XML
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) switch (c) {
            case '&': b.append("&amp;"); break; case '<': b.append("&lt;"); break; case '>': b.append("&gt;"); break;
            case '"': b.append("&quot;"); break; case '\'': b.append("&apos;"); break;
            default: if (c >= 0x20 || c == '\n' || c == '\t') b.append(c);
        }
        return b.toString();
    }
    /** كل المبالغ بثلاث خانات عشرية: مجاميع الرأس تساوي مجموع الأسطر تماماً */
    static double r9(double v) { return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue(); }
    static String n(double v, int dec) { return new BigDecimal(v).setScale(dec, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace("-0", "0"); }
    static String amt(double v) { return new BigDecimal(v).setScale(3, RoundingMode.HALF_UP).toPlainString(); }
    static String tot(double v) { return new BigDecimal(v).setScale(3, RoundingMode.HALF_UP).toPlainString(); }
    static final String CUR = " currencyID=\"JO\"";

    /** رمز نوع الفاتورة: الخانة الأولى 0 محلية، الثانية 1 نقدي / 2 ذمم، الثالثة 1 دخل / 2 مبيعات عامة */
    public static String typeCode(Invoice i) {
        Invoice base = i.type == InvType.SALE_RETURN && i.refId != 0 ? db().invoice(i.refId) : i;
        if (base == null) base = i;
        boolean credit = base.total - base.paid > 0.0005;
        return "0" + (credit ? "2" : "1") + (salesTax() ? "2" : "1");
    }

    /** قيم السطر الصافية (قبل الضريبة) بعد توزيع خصم الفاتورة: {الكمية، سعر الوحدة، الخصم، الصافي، الضريبة، النسبة} */
    static double[][] lineValues(Invoice inv) {
        boolean taxOn = salesTax() && db().getB("taxEnabled", true);
        double rate = db().getD("taxRate", 16) / 100.0;
        boolean incl = db().getB("taxInclusive", true);
        double gross = 0; for (InvLine l : inv.lines) gross += l.total();
        double[][] out = new double[inv.lines.size()][];
        for (int k = 0; k < inv.lines.size(); k++) {
            InvLine l = inv.lines.get(k);
            double share = gross > 0 ? inv.discount * l.total() / gross : 0;  // توزيع خصم الفاتورة على الأسطر
            double r = taxOn && l.taxable ? rate : 0, div = incl ? 1 + r : 1;
            double unit = r9(l.price / div);
            double net = r9((l.total() - share) / div);
            double disc = r9(l.qty * unit - net);
            if (disc < 0) { disc = 0; net = r9(l.qty * unit); }
            double tax = r9(net * r);
            net = r9(l.qty * unit - disc); // يضمن: الصافي = الكمية × السعر − الخصم تماماً
            out[k] = new double[]{l.qty, unit, disc, net, tax, r * 100};
        }
        return out;
    }

    public static String xml(Invoice inv) {
        boolean ret = inv.type == InvType.SALE_RETURN, tax = salesTax();
        Invoice orig = ret && inv.refId != 0 ? db().invoice(inv.refId) : null;
        double[][] v = lineValues(inv);
        double exc = 0, disc = 0, taxSum = 0, inc = 0;
        for (double[] x : v) { exc += x[0] * x[1]; disc += x[2]; taxSum += x[4]; inc += x[3] + x[4]; }
        Party p = db().party(inv.partyId);
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        b.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\" xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\" xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\" xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">");
        b.append("<cbc:ProfileID>reporting:1.0</cbc:ProfileID>");
        b.append("<cbc:ID>").append(esc((ret ? "R" : "") + inv.no)).append("</cbc:ID>");
        b.append("<cbc:UUID>").append(inv.einvUuid).append("</cbc:UUID>");
        b.append("<cbc:IssueDate>").append(inv.date.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("</cbc:IssueDate>");
        b.append("<cbc:InvoiceTypeCode name=\"").append(typeCode(inv)).append("\">").append(ret ? "381" : "388").append("</cbc:InvoiceTypeCode>");
        if (!ret && inv.notes != null && !inv.notes.trim().isEmpty()) b.append("<cbc:Note>").append(esc(inv.notes.trim())).append("</cbc:Note>");
        b.append("<cbc:DocumentCurrencyCode>JOD</cbc:DocumentCurrencyCode><cbc:TaxCurrencyCode>JOD</cbc:TaxCurrencyCode>");
        if (ret && orig != null) b.append("<cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>").append(orig.no).append("</cbc:ID><cbc:UUID>").append(orig.einvUuid)
                .append("</cbc:UUID><cbc:DocumentDescription>").append(tot(orig.total)).append("</cbc:DocumentDescription></cac:InvoiceDocumentReference></cac:BillingReference>");
        b.append("<cac:AdditionalDocumentReference><cbc:ID>ICV</cbc:ID><cbc:UUID>").append(inv.einvIcv).append("</cbc:UUID></cac:AdditionalDocumentReference>");
        // البائع
        b.append("<cac:AccountingSupplierParty><cac:Party><cac:PostalAddress><cac:Country><cbc:IdentificationCode>JO</cbc:IdentificationCode></cac:Country></cac:PostalAddress>")
         .append("<cac:PartyTaxScheme><cbc:CompanyID>").append(esc(db().get("jofTin", db().get("taxNo", "")))).append("</cbc:CompanyID><cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme>")
         .append("<cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(db().get("jofName", db().get("storeName", "")))).append("</cbc:RegistrationName></cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>");
        // المشتري
        String bid = p == null || p.taxId == null ? "" : p.taxId.trim(), scheme = p == null || p.idType == null || p.idType.isEmpty() ? "NIN" : p.idType;
        String phone = p == null || p.phone == null ? "" : p.phone.replaceAll("[^0-9]", "");
        b.append("<cac:AccountingCustomerParty><cac:Party><cac:PartyIdentification><cbc:ID schemeID=\"").append(scheme).append("\">").append(esc(bid)).append("</cbc:ID></cac:PartyIdentification>")
         .append("<cac:PostalAddress>");
        String gov = db().get("jofCity", "JO-AM");
        if (!gov.isEmpty()) b.append("<cbc:CountrySubentityCode>").append(esc(gov)).append("</cbc:CountrySubentityCode>");
        b.append("<cac:Country><cbc:IdentificationCode>JO</cbc:IdentificationCode></cac:Country></cac:PostalAddress>")
         .append("<cac:PartyTaxScheme>").append(bid.isEmpty() ? "" : "<cbc:CompanyID>" + esc(bid) + "</cbc:CompanyID>").append("<cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme>");
        if (p != null) b.append("<cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(p.name)).append("</cbc:RegistrationName></cac:PartyLegalEntity>");
        b.append("</cac:Party>");
        if (phone.length() >= 9 && phone.length() <= 14) b.append("<cac:AccountingContact><cbc:Telephone>").append(phone).append("</cbc:Telephone></cac:AccountingContact>");
        b.append("</cac:AccountingCustomerParty>");
        // تسلسل مصدر الدخل
        b.append("<cac:SellerSupplierParty><cac:Party><cac:PartyIdentification><cbc:ID>").append(esc(db().get("jofSeq", ""))).append("</cbc:ID></cac:PartyIdentification></cac:Party></cac:SellerSupplierParty>");
        if (ret) b.append("<cac:PaymentMeans><cbc:PaymentMeansCode listID=\"UN/ECE 4461\">10</cbc:PaymentMeansCode><cbc:InstructionNote>")
                .append(esc(inv.notes == null || inv.notes.trim().isEmpty() ? "إرجاع بضاعة" : inv.notes.trim())).append("</cbc:InstructionNote></cac:PaymentMeans>");
        b.append("<cac:AllowanceCharge><cbc:ChargeIndicator>false</cbc:ChargeIndicator><cbc:AllowanceChargeReason>discount</cbc:AllowanceChargeReason><cbc:Amount").append(CUR).append(">").append(tot(disc)).append("</cbc:Amount></cac:AllowanceCharge>");
        if (tax) b.append("<cac:TaxTotal><cbc:TaxAmount").append(CUR).append(">").append(tot(taxSum)).append("</cbc:TaxAmount></cac:TaxTotal>");
        b.append("<cac:LegalMonetaryTotal><cbc:TaxExclusiveAmount").append(CUR).append(">").append(tot(exc)).append("</cbc:TaxExclusiveAmount>")
         .append("<cbc:TaxInclusiveAmount").append(CUR).append(">").append(tot(inc)).append("</cbc:TaxInclusiveAmount>")
         .append("<cbc:AllowanceTotalAmount").append(CUR).append(">").append(tot(disc)).append("</cbc:AllowanceTotalAmount>")
         .append("<cbc:PayableAmount").append(CUR).append(">").append(tot(inc)).append("</cbc:PayableAmount></cac:LegalMonetaryTotal>");
        // الأسطر
        for (int k = 0; k < inv.lines.size(); k++) {
            InvLine l = inv.lines.get(k); double[] x = v[k];
            b.append("<cac:InvoiceLine><cbc:ID>").append(k + 1).append("</cbc:ID>")
             .append("<cbc:InvoicedQuantity unitCode=\"PCE\">").append(n(x[0], 9)).append("</cbc:InvoicedQuantity>")
             .append("<cbc:LineExtensionAmount").append(CUR).append(">").append(amt(x[3])).append("</cbc:LineExtensionAmount>");
            if (tax) {
                boolean s = x[5] > 0;
                b.append("<cac:TaxTotal><cbc:TaxAmount").append(CUR).append(">").append(amt(x[4])).append("</cbc:TaxAmount>")
                 .append("<cbc:RoundingAmount").append(CUR).append(">").append(amt(x[3] + x[4])).append("</cbc:RoundingAmount>")
                 .append("<cac:TaxSubtotal>");
                if (ret) b.append("<cbc:TaxableAmount").append(CUR).append(">").append(amt(x[3])).append("</cbc:TaxableAmount>");
                b.append("<cbc:TaxAmount").append(CUR).append(">").append(amt(x[4])).append("</cbc:TaxAmount>")
                 .append("<cac:TaxCategory><cbc:ID schemeAgencyID=\"6\" schemeID=\"UN/ECE 5305\">").append(s ? "S" : "Z").append("</cbc:ID>")
                 .append("<cbc:Percent>").append(n(x[5], 2)).append("</cbc:Percent>")
                 .append("<cac:TaxScheme><cbc:ID schemeAgencyID=\"6\" schemeID=\"UN/ECE 5153\">VAT</cbc:ID></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal></cac:TaxTotal>");
            }
            b.append("<cac:Item><cbc:Name>").append(esc(l.name + (l.factor > 1 ? " (" + l.unit + ")" : ""))).append("</cbc:Name></cac:Item>")
             .append("<cac:Price><cbc:PriceAmount").append(CUR).append(">").append(amt(x[1])).append("</cbc:PriceAmount>");
            if (ret) b.append("<cbc:BaseQuantity unitCode=\"C62\">1</cbc:BaseQuantity>");
            b.append("<cac:AllowanceCharge><cbc:ChargeIndicator>false</cbc:ChargeIndicator><cbc:AllowanceChargeReason>DISCOUNT</cbc:AllowanceChargeReason><cbc:Amount").append(CUR).append(">")
             .append(amt(x[2])).append("</cbc:Amount></cac:AllowanceCharge></cac:Price></cac:InvoiceLine>");
        }
        b.append("</Invoice>");
        return b.toString();
    }

    // ============================================================ الإرسال
    /** التحقق من الإعدادات قبل الإرسال */
    public static String checkSettings() {
        List<String> m = new ArrayList<>();
        if (db().get("jofClientId", "").trim().isEmpty()) m.add("Client ID");
        if (db().get("jofSecret", "").trim().isEmpty()) m.add("Secret Key");
        if (db().get("jofTin", db().get("taxNo", "")).trim().isEmpty()) m.add("الرقم الضريبي للمنشأة");
        if (db().get("jofSeq", "").trim().isEmpty()) m.add("تسلسل مصدر الدخل");
        if (db().get("jofName", db().get("storeName", "")).trim().isEmpty()) m.add("اسم المنشأة المسجّل");
        return m.isEmpty() ? "" : "أكمل إعدادات الفوترة الوطنية: " + String.join("، ", m);
    }

    /** يُرسل الفاتورة ويحدّث حالتها. يرجع نصاً يصف النتيجة */
    public static synchronized String submit(Invoice inv) {
        if (!applies(inv)) return "هذا النوع من الفواتير لا يُرسل";
        if (submitted(inv)) return "الفاتورة مرسلة ومقبولة مسبقاً";
        if (inv.cancelled) return "الفاتورة ملغاة";
        String chk = checkSettings(); if (!chk.isEmpty()) { inv.einvStatus = "PENDING"; inv.einvMsg = chk; Store.save(); return chk; }
        if (inv.type == InvType.SALE_RETURN) {
            Invoice o = inv.refId == 0 ? null : db().invoice(inv.refId);
            if (o == null) { inv.einvStatus = "PENDING"; inv.einvMsg = "المرتجع غير مرتبط بفاتورة أصلية (استخدم «استيراد من فاتورة»)"; Store.save(); return inv.einvMsg; }
            if (!submitted(o)) { inv.einvStatus = "PENDING"; inv.einvMsg = "يجب إرسال الفاتورة الأصلية رقم " + o.no + " أولاً"; Store.save(); return inv.einvMsg; }
        }
        if (inv.einvUuid == null || inv.einvUuid.isEmpty()) inv.einvUuid = UUID.randomUUID().toString();
        if (inv.einvIcv == 0) inv.einvIcv = db().next("jof_icv");
        String body = "{\"invoice\":\"" + Base64.getEncoder().encodeToString(xml(inv).getBytes(StandardCharsets.UTF_8)) + "\"}";
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(db().get("jofUrl", DEFAULT_URL).trim()).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Client-Id", db().get("jofClientId", "").trim()); c.setRequestProperty("Secret-Key", db().get("jofSecret", "").trim());
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json");
            try (java.io.OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String resp = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
            db().set("jofLastResponse", "HTTP " + code + "\n" + resp);
            return handle(inv, code, resp);
        } catch (Exception e) {
            inv.einvStatus = "PENDING"; inv.einvMsg = "تعذر الاتصال بنظام الفوترة: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " — " + e.getMessage());
            Store.save(); return inv.einvMsg;
        }
    }

    static String handle(Invoice inv, int code, String body) {
        Object js = null; try { js = Json.parse(body); } catch (Exception ignored) {}
        Map<?, ?> m = js instanceof Map ? (Map<?, ?>) js : Collections.emptyMap();
        String st = str(m.get("EINV_STATUS"));
        if (code == 200 && ("SUBMITTED".equals(st) || "ALREADY_SUBMITTED".equals(st))) {
            inv.einvStatus = st; inv.einvQr = str(m.get("EINV_QR")); inv.einvMsg = "";
            String uu = str(m.get("EINV_INV_UUID")); if (!uu.isEmpty()) inv.einvUuid = uu;
            if (inv.einvQr.isEmpty()) inv.einvMsg = "تم القبول لكن لم يصل رمز QR — راجع الرد";
            Store.save();
            return "تم قبول الفاتورة رقم " + inv.no + " في نظام الفوترة الوطني";
        }
        List<String> errs = new ArrayList<>();
        Object res = m.get("EINV_RESULTS");
        if (res instanceof Map) { Object e = ((Map<?, ?>) res).get("ERRORS");
            if (e instanceof List) for (Object o : (List<?>) e) if (o instanceof Map) errs.add(str(((Map<?, ?>) o).get("EINV_MESSAGE")) + " (" + str(((Map<?, ?>) o).get("EINV_CODE")) + ")"); }
        String msg;
        switch (code) {
            case 200: msg = errs.isEmpty() ? "رفضت الفاتورة: " + (st.isEmpty() ? "رد غير مفهوم" : st) : "رفضت الفاتورة: " + String.join(" | ", errs); break;
            case 400: msg = "خطأ في بيانات الفاتورة: " + (errs.isEmpty() ? str(m.get("EINV_MESSAGE")) + shortBody(body) : String.join(" | ", errs)); break;
            case 401: case 403: msg = "خطأ في Client ID أو Secret Key (HTTP " + code + ")"; break;
            case 500: msg = "رفض من النظام (HTTP 500): تحقق من الرقم الضريبي وتسلسل مصدر الدخل ونسبة الضريبة" + shortBody(body); break;
            case 504: msg = "لا يمكن الوصول إلى نظام الفوترة (HTTP 504) — تحقق من الإنترنت أو جدار الحماية"; break;
            default: msg = "رد غير متوقع HTTP " + code + shortBody(body);
        }
        inv.einvStatus = code == 200 || code == 400 || code == 500 ? "NOT_SUBMITTED" : "PENDING"; inv.einvMsg = msg; Store.save();
        return msg;
    }
    static String shortBody(String b) { if (b == null || b.trim().isEmpty()) return ""; String t = b.replaceAll("\\s+", " ").trim(); return " — " + (t.length() > 220 ? t.substring(0, 220) + "…" : t); }
    static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    static byte[] readAll(java.io.InputStream in) throws java.io.IOException { java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close(); return bo.toByteArray(); }

    public static String statusText(Invoice i) {
        if (!applies(i)) return "";
        switch (i.einvStatus == null ? "" : i.einvStatus) {
            case "SUBMITTED": case "ALREADY_SUBMITTED": return "مقبولة";
            case "NOT_SUBMITTED": return "مرفوضة";
            case "PENDING": return "بانتظار الإرسال";
            default: return "لم تُرسل";
        }
    }

    // ============================================================ JSON مبسّط
    static final class Json {
        final String s; int i;
        Json(String s) { this.s = s; }
        static Object parse(String s) { Json j = new Json(s.trim()); return j.val(); }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object val() {
            ws(); char c = s.charAt(i);
            if (c == '{') { i++; Map<String, Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = strv(); ws(); i++; m.put(k, val()); ws(); char d = s.charAt(i++); if (d == '}') return m; } }
            if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws(); if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(val()); ws(); char d = s.charAt(i++); if (d == ']') return l; } }
            if (c == '"') return strv();
            int st = i; while (i < s.length() && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) i++;
            String t = s.substring(st, i); return t.equals("null") ? null : t;
        }
        String strv() {
            StringBuilder b = new StringBuilder(); i++;
            while (true) { char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') { char e = s.charAt(i++);
                    switch (e) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(e); } }
                else b.append(c); }
        }
    }
}
