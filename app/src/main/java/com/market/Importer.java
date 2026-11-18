package com.market;

import com.market.Model.*;
import java.util.*;

/** استيراد المنتجات والعملاء والموردين من صفوف جدول (نفس منطق نسخة الكمبيوتر) */
public final class Importer {
    private Importer() {}
    public static final String[] PRODUCT_FIELDS = {"(تجاهل)", "الباركود", "اسم الصنف", "التصنيف", "التكلفة", "سعر البيع", "الكمية", "الوحدة", "الوحدة الكبرى", "عدد الحبات بالكرتونة", "سعر الوحدة الكبرى", "الحد الأدنى"};
    public static final String[] PARTY_FIELDS = {"— تجاهل —", "الاسم", "الهاتف", "العنوان", "الرصيد الافتتاحي", "سقف الائتمان", "الرقم الوطني / الضريبي", "ملاحظات"};

    public static int guessProduct(String h) {
        h = h == null ? "" : h.trim().toLowerCase();
        if (h.contains("باركود") || h.contains("barcode") || h.contains("كود") || h.equals("code")) return 1;
        if (h.contains("كرتون") && (h.contains("سعر"))) return 10;
        if (h.contains("عدد") || h.contains("factor") || h.contains("تعبئة")) return 9;
        if (h.contains("كبرى") || h.contains("كرتون")) return 8;
        if (h.contains("تكلفة") || h.contains("كلفة") || h.contains("شراء") || h.contains("cost")) return 4;
        if (h.contains("سعر") || h.contains("بيع") || h.contains("price")) return 5;
        if (h.contains("كمية") || h.contains("رصيد") || h.contains("qty") || h.contains("quantity")) return 6;
        if (h.contains("تصنيف") || h.contains("مجموعة") || h.contains("قسم") || h.contains("فئة") || h.contains("category")) return 3;
        if (h.contains("وحدة") || h.contains("unit")) return 7;
        if (h.contains("حد")) return 11;
        if (h.contains("اسم") || h.contains("صنف") || h.contains("منتج") || h.contains("name") || h.contains("بيان") || h.contains("item")) return 2;
        return 0;
    }
    public static int guessParty(String h) {
        h = h == null ? "" : h.trim().toLowerCase();
        if (h.contains("هاتف") || h.contains("جوال") || h.contains("موبايل") || h.contains("phone") || h.contains("mobile") || h.contains("تلفون")) return 2;
        if (h.contains("عنوان") || h.contains("address")) return 3;
        if (h.contains("سقف") || h.contains("limit")) return 5;
        if (h.contains("رصيد") || h.contains("دين") || h.contains("مبلغ") || h.contains("balance") || h.contains("ذمم")) return 4;
        if (h.contains("وطني") || h.contains("ضريبي") || h.contains("tax") || h.contains("رقم الهوية")) return 6;
        if (h.contains("ملاحظ") || h.contains("note")) return 7;
        if (h.contains("اسم") || h.contains("عميل") || h.contains("مورد") || h.contains("name") || h.contains("customer") || h.contains("supplier")) return 1;
        return 0;
    }
    /** إن لم يكن الصف الأول عناوين: أول صف فيه عنوان معروف خلال أول 5 صفوف */
    public static int headerRow(List<String[]> rows, boolean products) {
        for (int i = 0; i < Math.min(5, rows.size()); i++) { int hits = 0;
            for (String h : rows.get(i)) if ((products ? guessProduct(h) : guessParty(h)) > 0 && h.trim().length() < 40) hits++;
            if (hits >= 2) return i; }
        return 0;
    }
    static String val(String[] r, int i) { return i < 0 || i >= r.length || r[i] == null ? "" : r[i].trim(); }
    static double n(String s) { return Ui.num(s.replace(",", "").replace("٫", ".").replaceAll("[^0-9.\\-٠-٩]", "")); }

    /** يرجع {جديد، محدّث، متجاهل} */
    public static int[] products(List<String[]> data, int[] idx, int first, boolean update, boolean newCats) {
        int added = 0, updated = 0, skipped = 0;
        Map<String, Category> catMap = new HashMap<>(); for (Category c : Store.db.categories) catMap.put(c.name.trim(), c);
        Store.db.reindex();
        for (int i = first; i < data.size(); i++) {
            String[] r = data.get(i);
            String name = val(r, idx[2]); if (name.isEmpty()) { skipped++; continue; }
            String bc = val(r, idx[1]);
            Product p = bc.isEmpty() ? null : Store.db.byBarcode(bc);
            boolean isNew = p == null;
            if (!isNew && !update) { skipped++; continue; }
            if (isNew) { p = new Product(); p.barcode = bc; }
            p.name = name;
            String cn = val(r, idx[3]);
            if (!cn.isEmpty()) { Category c = catMap.get(cn);
                if (c == null && newCats) { c = new Category(); c.id = Store.db.next("cat"); c.name = cn; Store.db.categories.add(c); catMap.put(cn, c); }
                if (c != null) p.categoryId = c.id; }
            else if (isNew && !Store.db.categories.isEmpty()) p.categoryId = Store.db.categories.get(0).id;
            if (idx[4] >= 0) p.cost = n(val(r, idx[4]));
            if (idx[5] >= 0) p.price = n(val(r, idx[5]));
            if (idx[7] >= 0 && !val(r, idx[7]).isEmpty()) p.unit = val(r, idx[7]);
            if (idx[8] >= 0) p.bigUnit = val(r, idx[8]);
            if (idx[9] >= 0) p.factor = Math.max(1, n(val(r, idx[9])));
            if (idx[10] >= 0) p.bigPrice = n(val(r, idx[10]));
            if (idx[11] >= 0) p.minQty = n(val(r, idx[11]));
            double q = idx[6] >= 0 ? n(val(r, idx[6])) : 0;
            if (isNew) {
                p.id = Store.db.next("product"); Store.db.products.put(p.id, p);
                if (!bc.isEmpty()) Store.db.reindexOne(p);
                if (q != 0) Ops.stock(p, q, p.cost, "OPEN", 0, "استيراد من إكسل", java.time.LocalDateTime.now());
                added++;
            } else {
                if (idx[6] >= 0 && Math.abs(q - p.qty) > 1e-9) Ops.stock(p, q - p.qty, p.cost, "ADJUST", 0, "تحديث كمية من إكسل", java.time.LocalDateTime.now());
                updated++;
            }
        }
        Store.db.reindex(); Store.save();
        return new int[]{added, updated, skipped};
    }

    /** يرجع {جدد، محدّثون، متجاهلة، مجموع الأرصدة × 1000} */
    public static int[] parties(int kind, List<String[]> data, int[] idx, int first, boolean update, boolean flip) {
        int added = 0, updated = 0, skipped = 0; double total = 0;
        Map<String, Party> byName = new HashMap<>(), byPhone = new HashMap<>();
        for (Party p : Store.db.parties.values()) if (p.kind == kind) { byName.put(p.name.trim(), p); if (p.phone != null && !p.phone.trim().isEmpty()) byPhone.put(digits(p.phone), p); }
        for (int i = first; i < data.size(); i++) {
            String[] r = data.get(i);
            String name = val(r, idx[1]); if (name.isEmpty()) { skipped++; continue; }
            String phone = val(r, idx[2]);
            Party p = byName.get(name); if (p == null && !digits(phone).isEmpty()) p = byPhone.get(digits(phone));
            boolean isNew = p == null;
            if (!isNew && !update) { skipped++; continue; }
            if (isNew) { p = new Party(); p.kind = kind; p.id = Store.db.next("party"); p.created = java.time.LocalDateTime.now(); }
            p.name = name;
            if (idx[2] >= 0 && !phone.isEmpty()) p.phone = phone;
            if (idx[3] >= 0 && !val(r, idx[3]).isEmpty()) p.address = val(r, idx[3]);
            if (idx[5] >= 0) p.creditLimit = Math.max(0, n(val(r, idx[5])));
            if (idx[6] >= 0 && !val(r, idx[6]).isEmpty()) { p.taxId = val(r, idx[6]).replaceAll("\\s", ""); p.idType = p.taxId.length() == 10 ? "NIN" : "TN"; }
            if (idx[7] >= 0 && !val(r, idx[7]).isEmpty()) p.notes = val(r, idx[7]);
            if (isNew) { Store.db.parties.put(p.id, p); byName.put(p.name, p); if (!digits(p.phone).isEmpty()) byPhone.put(digits(p.phone), p); added++; } else updated++;
            if (idx[4] >= 0) { String b = val(r, idx[4]);
                if (!b.isEmpty()) { double v = n(b); if (flip) v = -v; Ops.setOpeningNoPersist(p, v); total += v; } }
        }
        Store.save();
        return new int[]{added, updated, skipped, (int) Math.round(total * 1000)};
    }
    static String digits(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }
}
