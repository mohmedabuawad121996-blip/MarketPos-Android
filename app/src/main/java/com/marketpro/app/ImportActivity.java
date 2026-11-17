package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Xml;
import android.widget.LinearLayout;

import com.market.Model;
import com.market.Model.Category;
import com.market.Model.Party;
import com.market.Model.Product;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** الاستيراد من Excel (xlsx) أو CSV: المنتجات، العملاء، الموردون. الأعمدة تُعرف من عناوين الصف الأول */
public class ImportActivity extends Activity {
    String what; static final int PICK = 51;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        what = getIntent().getStringExtra("what"); if (what == null) what = "products";
        String title = what.equals("products") ? "استيراد المنتجات من إكسل" : what.equals("customers") ? "استيراد العملاء من إكسل" : "استيراد الموردين من إكسل";
        LinearLayout body = Ux.screen(this, title, true);
        LinearLayout c = Ux.card(this); body.addView(c);
        c.addView(Ux.text(this, "اختر ملف Excel (.xlsx) أو CSV. الصف الأول يجب أن يحوي عناوين الأعمدة:", 14, true, Ux.TEXT));
        c.addView(Ux.text(this, what.equals("products")
                ? "• الاسم (مطلوب)\n• الباركود\n• سعر البيع\n• التكلفة\n• الكمية\n• الوحدة\n• التصنيف\n• الحد الأدنى\n\nالصنف الموجود بنفس الباركود يُحدَّث اسمه وأسعاره، والجديد يُضاف بكميته الافتتاحية."
                : "• الاسم (مطلوب)\n• الهاتف\n• العنوان\n• الرصيد (الرصيد الافتتاحي)\n\nالاسم الموجود مسبقاً يُتخطّى.", 13, false, Ux.MUTED));
        Ux.add(c, Ux.button(this, "اختيار الملف", Ux.PRIMARY, v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, PICK); }), 10);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        Ux.run(this, () -> { try {
            byte[] bytes; try (InputStream in = getContentResolver().openInputStream(u)) { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); bytes = bo.toByteArray(); }
            List<String[]> rows = bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K' ? xlsx(bytes) : csv(bytes);
            if (rows.size() < 2) throw new Ops.OpException("الملف فارغ أو بلا بيانات تحت العناوين");
            String r = what.equals("products") ? products(rows) : parties(rows, what.equals("customers") ? Model.CUSTOMER : Model.SUPPLIER);
            Ux.alert(this, r);
        } catch (Ops.OpException e) { throw e; } catch (Exception e) { throw new RuntimeException("تعذّرت قراءة الملف: " + e.getMessage(), e); } });
    }

    static int col(String[] head, String... keys) {
        for (int i = 0; i < head.length; i++) { String h = head[i] == null ? "" : head[i].trim().toLowerCase(); for (String k : keys) if (h.equals(k) || h.contains(k)) return i; }
        return -1;
    }
    static String at(String[] r, int i) { return i < 0 || i >= r.length || r[i] == null ? "" : r[i].trim(); }

    String products(List<String[]> rows) {
        String[] h = rows.get(0);
        int name = col(h, "الاسم", "اسم الصنف", "الصنف", "name", "item"), bc = col(h, "الباركود", "باركود", "barcode", "code"), price = col(h, "سعر البيع", "البيع", "السعر", "price"),
            cost = col(h, "التكلفة", "الكلفة", "سعر الشراء", "cost"), qty = col(h, "الكمية", "الرصيد", "qty", "quantity"), unit = col(h, "الوحدة", "unit"),
            cat = col(h, "التصنيف", "الفئة", "category"), min = col(h, "الحد الأدنى", "حد الطلب", "min");
        if (name < 0) throw new Ops.OpException("لم أجد عمود «الاسم» في الصف الأول");
        int added = 0, updated = 0, skipped = 0;
        for (int i = 1; i < rows.size(); i++) { String[] r = rows.get(i); String nm = at(r, name); if (nm.isEmpty()) { skipped++; continue; }
            String code = at(r, bc); Product ex = code.isEmpty() ? null : Store.db.byBarcode(code);
            if (ex != null) { ex.name = nm; if (price >= 0 && !at(r, price).isEmpty()) ex.price = Ui.num(at(r, price)); if (cost >= 0 && !at(r, cost).isEmpty()) ex.cost = Ui.num(at(r, cost));
                if (min >= 0 && !at(r, min).isEmpty()) ex.minQty = Ui.num(at(r, min)); updated++; continue; }
            Product p = new Product(); p.name = nm; p.barcode = code; p.price = Ui.num(at(r, price)); p.cost = Ui.num(at(r, cost)); p.minQty = Ui.num(at(r, min));
            if (!at(r, unit).isEmpty()) p.unit = at(r, unit);
            p.categoryId = category(at(r, cat));
            try { Ops.addProduct(p, Ui.num(at(r, qty))); added++; } catch (Ops.OpException e) { skipped++; }
        }
        Store.db.reindex(); Store.save();
        return "تم الاستيراد ✓\nأصناف جديدة: " + added + "\nأصناف حُدّثت: " + updated + (skipped > 0 ? "\nأسطر متخطّاة: " + skipped : "");
    }
    long category(String n) {
        if (n.isEmpty()) return Store.db.categories.isEmpty() ? 0 : Store.db.categories.get(0).id;
        for (Category c : Store.db.categories) if (c.name.equals(n)) return c.id;
        Category c = new Category(); c.id = Store.db.next("cat"); c.name = n; Store.db.categories.add(c); return c.id;
    }
    String parties(List<String[]> rows, int kind) {
        String[] h = rows.get(0);
        int name = col(h, "الاسم", "اسم", "name"), phone = col(h, "الهاتف", "الجوال", "الموبايل", "phone", "mobile"), addr = col(h, "العنوان", "address"), bal = col(h, "الرصيد", "رصيد", "balance");
        if (name < 0) throw new Ops.OpException("لم أجد عمود «الاسم» في الصف الأول");
        int added = 0, skipped = 0;
        for (int i = 1; i < rows.size(); i++) { String[] r = rows.get(i); String nm = at(r, name); if (nm.isEmpty()) { skipped++; continue; }
            boolean dup = false; for (Party x : Store.db.parties(kind)) if (x.name.equals(nm)) { dup = true; break; } if (dup) { skipped++; continue; }
            Party p = new Party(); p.id = Store.db.next("party"); p.kind = kind; p.name = nm; p.phone = at(r, phone); p.address = at(r, addr);
            Store.db.parties.put(p.id, p);
            double b = Ui.num(at(r, bal)); if (Math.abs(b) > 0.0005) Ops.setOpening(p, b);
            added++; }
        Store.save();
        return "تم الاستيراد ✓\nأُضيف: " + added + (skipped > 0 ? "\nمتخطّى (فارغ أو مكرر): " + skipped : "");
    }

    // ------------------------------------------------ قراءة الملفات
    static List<String[]> csv(byte[] b) throws Exception {
        String s = new String(b, StandardCharsets.UTF_8); if (s.startsWith("﻿")) s = s.substring(1);
        char sep = s.indexOf(';') >= 0 && s.indexOf(',') < 0 ? ';' : s.indexOf('\t') >= 0 && s.indexOf(',') < 0 ? '\t' : ',';
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) { if (line.trim().isEmpty()) continue;
                List<String> cells = new ArrayList<>(); StringBuilder cur = new StringBuilder(); boolean q = false;
                for (int i = 0; i < line.length(); i++) { char c = line.charAt(i);
                    if (c == '"') { if (q && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else q = !q; }
                    else if (c == sep && !q) { cells.add(cur.toString()); cur.setLength(0); } else cur.append(c); }
                cells.add(cur.toString()); out.add(cells.toArray(new String[0])); }
        }
        return out;
    }
    static List<String[]> xlsx(byte[] b) throws Exception {
        byte[] shared = null, sheet = null; String firstSheet = null; java.util.Map<String, byte[]> sheets = new java.util.TreeMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName();
                if (n.equals("xl/sharedStrings.xml") || n.startsWith("xl/worksheets/sheet")) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int k; while ((k = z.read(buf)) > 0) bo.write(buf, 0, k);
                    if (n.equals("xl/sharedStrings.xml")) shared = bo.toByteArray(); else sheets.put(n, bo.toByteArray());
                }
            }
        }
        if (sheets.isEmpty()) throw new Exception("لا توجد أوراق في الملف");
        sheet = sheets.containsKey("xl/worksheets/sheet1.xml") ? sheets.get("xl/worksheets/sheet1.xml") : sheets.values().iterator().next();
        List<String> ss = new ArrayList<>();
        if (shared != null) { XmlPullParser p = Xml.newPullParser(); p.setInput(new ByteArrayInputStream(shared), "UTF-8"); StringBuilder cur = null;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG && p.getName().equals("si")) cur = new StringBuilder();
                else if (ev == XmlPullParser.START_TAG && p.getName().equals("t") && cur != null) cur.append(p.nextText());
                else if (ev == XmlPullParser.END_TAG && p.getName().equals("si")) { ss.add(cur == null ? "" : cur.toString()); cur = null; } } }
        List<String[]> out = new ArrayList<>();
        XmlPullParser p = Xml.newPullParser(); p.setInput(new ByteArrayInputStream(sheet), "UTF-8");
        List<String> row = null; String ref = null, type = null; StringBuilder val = null;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) { String n = p.getName();
                if (n.equals("row")) row = new ArrayList<>();
                else if (n.equals("c")) { ref = p.getAttributeValue(null, "r"); type = p.getAttributeValue(null, "t"); val = new StringBuilder(); }
                else if ((n.equals("v") || n.equals("t")) && val != null) val.append(p.nextText()); }
            else if (ev == XmlPullParser.END_TAG) { String n = p.getName();
                if (n.equals("c") && row != null) { int ci = ref == null ? row.size() : colIndex(ref); while (row.size() < ci) row.add("");
                    String v = val == null ? "" : val.toString();
                    if ("s".equals(type)) { try { v = ss.get(Integer.parseInt(v.trim())); } catch (Exception ignored) {} }
                    else if (v.matches("-?\\d+\\.0+")) v = v.substring(0, v.indexOf('.'));
                    row.add(v); val = null; }
                else if (n.equals("row") && row != null) { boolean any = false; for (String x : row) if (!x.trim().isEmpty()) any = true; if (any) out.add(row.toArray(new String[0])); row = null; } }
        }
        return out;
    }
    static int colIndex(String ref) { int c = 0; for (char ch : ref.toCharArray()) { if (!Character.isLetter(ch)) break; c = c * 26 + (Character.toUpperCase(ch) - 'A' + 1); } return Math.max(0, c - 1); }
}
