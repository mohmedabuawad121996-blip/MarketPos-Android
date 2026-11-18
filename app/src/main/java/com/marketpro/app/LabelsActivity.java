package com.marketpro.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.text.Layout;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.market.Model.Product;
import com.market.Store;
import com.market.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** طباعة ملصقات الباركود: ملف PDF (ورقة A4 أو لفة ملصقات بمقاس محدد) */
public class LabelsActivity extends Activity {
    final Map<Product, Integer> items = new LinkedHashMap<>(); Ux.Adapter<Product> ad; TextView info;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "طباعة ملصقات الباركود", false);
        LinearLayout r = Ux.row(this);
        r.addView(Ux.button(this, "+ إضافة صنف", Ux.GREEN, v -> add()), Ux.weight(1));
        View g = new View(this); r.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        r.addView(Ux.button(this, "المقاس", Ux.TEAL, v -> size()), Ux.weight(1));
        body.addView(r);
        info = Ux.text(this, "", 13, false, Ux.MUTED); Ux.add(body, info, 6);
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>((p, i) -> Ux.listRow(this, p.name, p.barcode + "  •  " + Ui.m(p.price), items.get(p) + " ملصق", Ux.PRIMARY)); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> { Product x = ad.getItem(i);
            Ux.input(this, "عدد الملصقات لـ " + x.name + " (0 للحذف)", "العدد", String.valueOf(items.get(x)), true, s -> { int n = (int) Ui.num(s); if (n <= 0) items.remove(x); else items.put(x, n); load(); }); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 6); body.addView(list, lp);
        Ux.add(body, Ux.button(this, "إنشاء PDF للطباعة", Ux.PRIMARY, v -> make()), 6);
        long pid = getIntent().getLongExtra("product", 0); if (pid != 0) { Product x = Store.db.products.get(pid); if (x != null) items.put(x, 1); }
        load();
    }
    String mode() { return Store.db.get("lblMode", "a4"); }
    void load() {
        ad.set(new ArrayList<>(items.keySet())); int n = 0; for (int x : items.values()) n += x;
        info.setText("عدد الملصقات: " + n + "   |   المقاس: " + (mode().equals("a4") ? "ورقة A4 (3 × 8)" : "لفة " + Ui.q(Store.db.getD("lblW", 40)) + "×" + Ui.q(Store.db.getD("lblH", 25)) + " مم"));
    }
    void add() {
        Ux.input(this, "ابحث عن الصنف", "الاسم أو الباركود", "", false, q -> {
            List<Product> ps = new ArrayList<>(); List<String> n = new ArrayList<>(); String k = q.toLowerCase();
            for (Product x : Store.db.products.values()) if (x.active && (x.name.toLowerCase().contains(k) || (x.barcode != null && x.barcode.contains(k)))) { ps.add(x); n.add(x.name + "  —  " + Ui.m(x.price)); if (ps.size() >= 80) break; }
            if (ps.isEmpty()) { Ux.alert(this, "لا يوجد صنف مطابق"); return; }
            Ux.choose(this, "الصنف", n, i -> Ux.input(this, "عدد الملصقات", "العدد", "1", true, s -> { int c = (int) Math.max(1, Ui.num(s)); items.merge(ps.get(i), c, Integer::sum); load(); }));
        });
    }
    void size() {
        Ux.choose(this, "مقاس الملصقات", java.util.Arrays.asList("ورقة A4 (24 ملصق: 3 × 8)", "لفة ملصقات (طابعة ملصقات) بمقاس محدد"), i -> {
            if (i == 0) { Store.db.set("lblMode", "a4"); Store.save(); load(); }
            else Ux.input(this, "عرض الملصق بالمليمتر", "40", Ui.q(Store.db.getD("lblW", 40)), true, w -> Ux.input(this, "ارتفاع الملصق بالمليمتر", "25", Ui.q(Store.db.getD("lblH", 25)), true, h -> {
                Store.db.set("lblMode", "roll"); Store.db.set("lblW", String.valueOf(Math.max(20, Ui.num(w)))); Store.db.set("lblH", String.valueOf(Math.max(15, Ui.num(h)))); Store.save(); load(); }));
        });
    }
    static float mm(double v) { return (float) (v * 72 / 25.4); }
    void make() {
        if (items.isEmpty()) { Ux.alert(this, "أضف أصنافاً أولاً"); return; }
        Ux.run(this, () -> { try {
            PdfDocument pdf = new PdfDocument(); boolean a4 = mode().equals("a4");
            int pw = a4 ? 595 : Math.round(mm(Store.db.getD("lblW", 40))), ph = a4 ? 842 : Math.round(mm(Store.db.getD("lblH", 25)));
            int cols = a4 ? 3 : 1, rows = a4 ? 8 : 1; float mx = a4 ? 10 : 0, my = a4 ? 22 : 0;
            float cw = (pw - 2 * mx) / cols, chh = (ph - 2 * my) / rows;
            List<Product> all = new ArrayList<>(); for (Map.Entry<Product, Integer> e : items.entrySet()) for (int i = 0; i < e.getValue(); i++) all.add(e.getKey());
            PdfDocument.Page page = null; int n = 0, per = cols * rows;
            for (Product p : all) {
                if (n % per == 0) { if (page != null) pdf.finishPage(page); page = pdf.startPage(new PdfDocument.PageInfo.Builder(pw, ph, n / per + 1).create()); }
                int k = n % per; float x = mx + (cols - 1 - k % cols) * cw, y = my + (k / cols) * chh;
                label(page.getCanvas(), p, x, y, cw, chh); n++;
            }
            pdf.finishPage(page);
            File dir = new File(getCacheDir(), "share"); dir.mkdirs(); File f = new File(dir, "ملصقات_الباركود.pdf");
            try (FileOutputStream o = new FileOutputStream(f)) { pdf.writeTo(o); } pdf.close();
            PdfViewActivity.open(this, f, "ملصقات الباركود");
        } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); } });
    }
    void label(Canvas c, Product p, float x, float y, float w, float h) {
        float pad = Math.max(2, w * 0.04f), iw = w - 2 * pad; float yy = y + pad;
        String store = Store.db.get("storeName", "");
        float fs = Math.max(5, h * 0.11f);
        if (!store.isEmpty()) yy += Pdf.draw(c, store, Pdf.paint(fs * 0.85f, false, 0xFF475569), (int) (x + pad), (int) yy, (int) iw, Layout.Alignment.ALIGN_CENTER);
        yy += Pdf.draw(c, p.name, Pdf.paint(fs, true, Color.BLACK), (int) (x + pad), (int) yy, (int) iw, Layout.Alignment.ALIGN_CENTER);
        float priceH = fs * 1.6f, digitsH = fs * 1.1f, bh = Math.max(8, y + h - pad - yy - priceH - digitsH);
        String code = p.barcode == null ? "" : p.barcode.trim();
        if (!code.isEmpty()) {
            Bitmap bc = Codes.barcode(code, 600, 160);
            if (bc != null) { float bw = Math.min(iw, iw * 0.95f); c.drawBitmap(bc, null, new Rect((int) (x + (w - bw) / 2), (int) yy, (int) (x + (w + bw) / 2), (int) (yy + bh)), new Paint(Paint.FILTER_BITMAP_FLAG)); }
            yy += bh;
            yy += Pdf.draw(c, code, Pdf.paint(fs * 0.85f, false, Color.BLACK), (int) (x + pad), (int) yy, (int) iw, Layout.Alignment.ALIGN_CENTER);
        }
        Pdf.draw(c, Ui.m(p.price) + " " + Store.db.get("currency", ""), Pdf.paint(fs * 1.35f, true, Color.BLACK), (int) (x + pad), (int) yy, (int) iw, Layout.Alignment.ALIGN_CENTER);
    }
}
