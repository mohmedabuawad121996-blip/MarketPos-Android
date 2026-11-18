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
        final List<String[]>[] out = new List[1];
        boolean ok = Ux.run(this, () -> { try {
            byte[] bytes; try (InputStream in = getContentResolver().openInputStream(u)) { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); bytes = bo.toByteArray(); }
            if (bytes.length > 8 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF) throw new Ops.OpException("صيغة xls القديمة غير مدعومة. افتح الملف في إكسل واحفظه بصيغة xlsx أو csv");
            out[0] = bytes.length > 2 && bytes[0] == 'P' && bytes[1] == 'K' ? xlsx(bytes) : csv(bytes);
        } catch (Ops.OpException e) { throw e; } catch (Exception e) { throw new RuntimeException("تعذّرت قراءة الملف: " + e.getMessage(), e); } });
        if (!ok) return;
        List<String[]> rows = new ArrayList<>(); for (String[] r : out[0]) { boolean any = false; for (String x : r) if (x != null && !x.trim().isEmpty()) any = true; if (any) rows.add(r); }
        if (rows.isEmpty()) { Ux.alert(this, "الملف فارغ"); return; }
        mapping(rows);
    }

    /** ربط الأعمدة: نفس نافذة «ربط الأعمدة» في برنامج الكمبيوتر */
    void mapping(List<String[]> rows) {
        boolean prod = what.equals("products");
        String[] fields = prod ? com.market.Importer.PRODUCT_FIELDS : com.market.Importer.PARTY_FIELDS;
        int cols = 0; for (String[] r : rows) cols = Math.max(cols, r.length);
        int hr = com.market.Importer.headerRow(rows, prod); String[] head = rows.get(hr);
        int[] sel = new int[cols];
        for (int c = 0; c < cols; c++) sel[c] = prod ? com.market.Importer.guessProduct(c < head.length ? head[c] : "") : com.market.Importer.guessParty(c < head.length ? head[c] : "");
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p = Ux.dp(this, 14); box.setPadding(p, Ux.dp(this, 6), p, 0);
        box.addView(Ux.text(this, "عدد الصفوف: " + rows.size() + " — اربط كل عمود بالحقل المناسب:", 13, true, Ux.TEXT));
        // معاينة أول الصفوف
        android.widget.TableLayout t = new android.widget.TableLayout(this); t.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < Math.min(6, rows.size()); i++) { android.widget.TableRow tr = new android.widget.TableRow(this); tr.setBackgroundColor(i == hr ? Ux.HEAD : i % 2 == 0 ? Ux.ROW : Ux.ROW_ALT);
            for (int c = 0; c < cols; c++) { android.widget.TextView tv = Ux.text(this, c < rows.get(i).length ? rows.get(i)[c] : "", 12, i == hr, Ux.TEXT); tv.setPadding(Ux.dp(this, 6), Ux.dp(this, 4), Ux.dp(this, 6), Ux.dp(this, 4)); tv.setMaxWidth(Ux.dp(this, 140)); tv.setSingleLine(true); tr.addView(tv); }
            t.addView(tr); }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this); hs.addView(t); Ux.add(box, hs, 6);
        List<android.widget.Button> bs = new ArrayList<>();
        for (int c = 0; c < cols; c++) { final int col = c; String h = c < head.length ? head[c] : "";
            android.widget.Button b = Ux.button(this, "", sel[c] > 0 ? Ux.PRIMARY : Ux.GRAY, null); bs.add(b);
            Runnable lbl = () -> { b.setText("العمود " + (col + 1) + (h.isEmpty() ? "" : " «" + (h.length() > 18 ? h.substring(0, 18) : h) + "»") + "  ←  " + fields[sel[col]]); };
            lbl.run();
            b.setOnClickListener(v -> Ux.choose(this, "العمود " + (col + 1) + ": " + h, java.util.Arrays.asList(fields), i -> { sel[col] = i; lbl.run(); b.setBackground(Ux.round(i > 0 ? Ux.PRIMARY : Ux.GRAY, 8, this)); }));
            Ux.add(box, b, 6); }
        android.widget.CheckBox hasHead = new android.widget.CheckBox(this); hasHead.setText("الصف " + (hr + 1) + " عناوين (لا يُستورد)"); hasHead.setChecked(true); Ux.add(box, hasHead, 8);
        android.widget.CheckBox upd = new android.widget.CheckBox(this); upd.setText(prod ? "تحديث الأصناف الموجودة (حسب الباركود)" : "تحديث الموجودين (حسب الاسم أو الهاتف)"); upd.setChecked(true); box.addView(upd);
        android.widget.CheckBox extra = new android.widget.CheckBox(this); extra.setText(prod ? "إنشاء التصنيفات غير الموجودة" : "عكس إشارة الرصيد"); extra.setChecked(prod); box.addView(extra);
        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(box);
        new Dlg(this).setTitle(prod ? "استيراد المنتجات - ربط الأعمدة" : "استيراد - ربط الأعمدة").setView(sv)
                .setPositiveButton("بدء الاستيراد", (d, w) -> {
                    int[] idx = new int[fields.length]; java.util.Arrays.fill(idx, -1);
                    for (int c = 0; c < sel.length; c++) if (sel[c] > 0) idx[sel[c]] = c;
                    if (idx[prod ? 2 : 1] < 0) { Ux.alert(this, prod ? "يجب ربط عمود اسم الصنف" : "يجب ربط عمود الاسم"); mapping(rows); return; }
                    int first = hasHead.isChecked() ? hr + 1 : hr;
                    try { Store.backupTo(Store.dataDir.resolve("backups"), "before_import"); } catch (Exception ignored) {}
                    final int[][] r = new int[1][];
                    if (!Ux.run(this, () -> r[0] = prod ? com.market.Importer.products(rows, idx, first, upd.isChecked(), extra.isChecked())
                            : com.market.Importer.parties(what.equals("customers") ? Model.CUSTOMER : Model.SUPPLIER, rows, idx, first, upd.isChecked(), extra.isChecked()))) return;
                    Ux.alert(this, prod ? "تم الاستيراد بنجاح ✓\nأصناف جديدة: " + r[0][0] + "\nأصناف محدّثة: " + r[0][1] + "\nصفوف متجاهلة: " + r[0][2]
                            : "تم الاستيراد بنجاح ✓\nجدد: " + r[0][0] + "\nمحدّثون: " + r[0][1] + "\nصفوف متجاهلة: " + r[0][2] + "\nإجمالي الأرصدة: " + Ui.m(r[0][3] / 1000.0));
                }).setNegativeButton("إلغاء", null).show();
    }

    // ------------------------------------------------ قراءة الملفات
    static List<String[]> csv(byte[] b) throws Exception {
        String s = new String(b, StandardCharsets.UTF_8);
        if (s.contains("\uFFFD")) s = new String(b, java.nio.charset.Charset.forName("windows-1256"));
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        String firstLine = s.split("\r?\n", 2)[0]; long semi = 0, comma = 0; for (char c : firstLine.toCharArray()) { if (c == ';') semi++; else if (c == ',') comma++; }
        char sep = semi > comma ? ';' : firstLine.indexOf('\t') >= 0 ? '\t' : ',';
        List<String[]> rows = new ArrayList<>(); List<String> cur = new ArrayList<>(); StringBuilder fld = new StringBuilder(); boolean q = false;
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i);
            if (q) { if (c == '"') { if (i + 1 < s.length() && s.charAt(i + 1) == '"') { fld.append('"'); i++; } else q = false; } else fld.append(c); }
            else if (c == '"') q = true;
            else if (c == sep) { cur.add(fld.toString()); fld.setLength(0); }
            else if (c == '\n' || c == '\r') { if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++; cur.add(fld.toString()); fld.setLength(0); rows.add(cur.toArray(new String[0])); cur = new ArrayList<>(); }
            else fld.append(c); }
        if (fld.length() > 0 || !cur.isEmpty()) { cur.add(fld.toString()); rows.add(cur.toArray(new String[0])); }
        return rows;
    }
    static byte[] entry(java.util.Map<String, byte[]> z, String n) { return z.get(n); }
    static List<String[]> xlsx(byte[] b) throws Exception {
        java.util.Map<String, byte[]> z = new java.util.HashMap<>();
        try (ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(b))) {
            for (ZipEntry e; (e = zi.getNextEntry()) != null; ) { String n = e.getName(); if (!n.startsWith("xl/")) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[65536]; int k; while ((k = zi.read(buf)) > 0) bo.write(buf, 0, k); z.put(n, bo.toByteArray()); }
        }
        // الورقة الأولى حسب ترتيب المصنف
        String sheet = "xl/worksheets/sheet1.xml";
        try { String rid = null; XmlPullParser p = parser(z.get("xl/workbook.xml"));
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT && rid == null; ev = p.next())
                if (ev == XmlPullParser.START_TAG && "sheet".equals(ln(p))) for (int i = 0; i < p.getAttributeCount(); i++) if (p.getAttributeName(i).endsWith("id")) rid = p.getAttributeValue(i);
            if (rid != null) { XmlPullParser r = parser(z.get("xl/_rels/workbook.xml.rels"));
                for (int ev = r.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = r.next())
                    if (ev == XmlPullParser.START_TAG && "Relationship".equals(ln(r)) && rid.equals(r.getAttributeValue(null, "Id"))) { String t = r.getAttributeValue(null, "Target");
                        sheet = t.startsWith("/") ? t.substring(1) : "xl/" + t; break; } }
        } catch (Exception ignored) {}
        if (!z.containsKey(sheet)) { for (String k : new java.util.TreeSet<>(z.keySet())) if (k.startsWith("xl/worksheets/sheet")) { sheet = k; break; } }
        if (!z.containsKey(sheet)) throw new Exception("لم يتم العثور على ورقة عمل في الملف");
        List<String> ss = new ArrayList<>();
        if (z.containsKey("xl/sharedStrings.xml")) { XmlPullParser p = parser(z.get("xl/sharedStrings.xml")); StringBuilder cur = null; boolean inT = false, rph = false;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) { String n = ln(p); if (n.equals("si")) cur = new StringBuilder(); else if (n.equals("rPh")) rph = true; else if (n.equals("t") && !rph) inT = true; }
                else if (ev == XmlPullParser.TEXT) { if (inT && cur != null) cur.append(p.getText()); }
                else if (ev == XmlPullParser.END_TAG) { String n = ln(p); if (n.equals("t")) inT = false; else if (n.equals("rPh")) rph = false; else if (n.equals("si")) { ss.add(cur == null ? "" : cur.toString()); cur = null; } } } }
        List<String[]> first = rowsOf(z.get(sheet), ss);
        if (!first.isEmpty()) return first;
        for (String k : new java.util.TreeSet<>(z.keySet())) if (k.startsWith("xl/worksheets/sheet") && k.endsWith(".xml") && !k.equals(sheet)) { List<String[]> o = rowsOf(z.get(k), ss); if (!o.isEmpty()) return o; }
        return first;
    }
    static List<String[]> rowsOf(byte[] data, List<String> ss) throws Exception {
        List<String[]> out = new ArrayList<>();
        XmlPullParser p = parser(data);
        java.util.TreeMap<Integer, String> row = null; String type = null; int col = 0, next = 0; StringBuilder val = null; boolean inV = false;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) { String n = ln(p);
                if (n.equals("row")) { row = new java.util.TreeMap<>(); next = 0; }
                else if (n.equals("c")) { type = p.getAttributeValue(null, "t"); String ref = p.getAttributeValue(null, "r"); col = ref == null ? next : colIndex(ref); next = col + 1; val = new StringBuilder(); }
                else if (n.equals("v") || (n.equals("t") && "inlineStr".equals(type))) inV = true; }
            else if (ev == XmlPullParser.TEXT) { if (inV && val != null) val.append(p.getText()); }
            else if (ev == XmlPullParser.END_TAG) { String n = ln(p);
                if (n.equals("v") || n.equals("t")) inV = false;
                else if (n.equals("c") && row != null && val != null) { String v = val.toString();
                    if ("s".equals(type)) { try { v = ss.get(Integer.parseInt(v.trim())); } catch (Exception ignored) {} }
                    else if (type == null || "n".equals(type)) v = cleanNum(v);
                    row.put(col, v); val = null; }
                else if (n.equals("row") && row != null) { int max = row.isEmpty() ? 0 : row.lastKey() + 1; String[] a = new String[max]; java.util.Arrays.fill(a, "");
                    for (java.util.Map.Entry<Integer, String> e : row.entrySet()) a[e.getKey()] = e.getValue(); out.add(a); row = null; } }
        }
        return out;
    }
    static String ln(XmlPullParser p) { String n = p.getName(); int i = n == null ? -1 : n.indexOf(':'); return i < 0 ? n : n.substring(i + 1); }
    static XmlPullParser parser(byte[] b) throws Exception { XmlPullParser p = Xml.newPullParser(); p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); p.setInput(new ByteArrayInputStream(b), "UTF-8"); return p; }
    static String cleanNum(String v) {
        try { double d = Double.parseDouble(v.trim()); if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d); return String.valueOf(d); } catch (Exception e) { return v; }
    }
    static int colIndex(String ref) { int c = 0; for (char ch : ref.toCharArray()) { if (!Character.isLetter(ch)) break; c = c * 26 + (Character.toUpperCase(ch) - 'A' + 1); } return Math.max(0, c - 1); }
}
