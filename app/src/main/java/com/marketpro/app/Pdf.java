package com.marketpro.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import com.market.Model.InvLine;
import com.market.Model.Invoice;
import com.market.ReportApi;
import com.market.Store;
import com.market.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** إنشاء ملفات PDF (تقارير A4 وفواتير) ومشاركتها أو طباعتها من الهاتف */
public final class Pdf {
    private Pdf() {}
    static final int M = 28; // الهامش

    static final class Doc {
        final PdfDocument pdf = new PdfDocument(); PdfDocument.Page page; Canvas c; int w, h, y, no;
        Doc(boolean landscape) { w = landscape ? 842 : 595; h = landscape ? 595 : 842; }
        void newPage() { if (page != null) pdf.finishPage(page); page = pdf.startPage(new PdfDocument.PageInfo.Builder(w, h, ++no).create()); c = page.getCanvas(); y = M; }
        File save(Activity a, String name) throws Exception {
            if (page != null) pdf.finishPage(page);
            File dir = new File(a.getCacheDir(), "share"); dir.mkdirs();
            File f = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf");
            try (FileOutputStream o = new FileOutputStream(f)) { pdf.writeTo(o); } pdf.close(); return f;
        }
    }
    static TextPaint paint(float size, boolean bold, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG); p.setTextSize(size); p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); return p;
    }
    static StaticLayout layout(String s, TextPaint p, int width, Layout.Alignment al) {
        return StaticLayout.Builder.obtain(s == null ? "" : s, 0, s == null ? 0 : s.length(), p, Math.max(10, width))
                .setAlignment(al).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(false).build();
    }
    /** يرسم نصاً داخل مستطيل (x من اليسار) ويعيد ارتفاعه */
    static int draw(Canvas c, String s, TextPaint p, int x, int y, int width, Layout.Alignment al) {
        StaticLayout l = layout(s, p, width, al); c.save(); c.translate(x, y); l.draw(c); c.restore(); return l.getHeight();
    }
    static final Layout.Alignment R = Layout.Alignment.ALIGN_NORMAL, CENTER = Layout.Alignment.ALIGN_CENTER, L = Layout.Alignment.ALIGN_OPPOSITE;

    /** ترويسة المحل */
    static void header(Doc d, String title, String sub) {
        String store = Store.db.settings.getOrDefault("storeName", ""), info = String.join("  •  ", nonEmpty(Store.db.settings.get("address"), Store.db.settings.get("phone"),
                Store.db.settings.getOrDefault("taxNo", "").isEmpty() ? "" : "الرقم الضريبي: " + Store.db.settings.get("taxNo")));
        int w = d.w - 2 * M;
        d.y += draw(d.c, store, paint(18, true, 0xFF065F46), M, d.y, w, CENTER);
        if (!info.isEmpty()) d.y += draw(d.c, info, paint(10, false, 0xFF475569), M, d.y + 2, w, CENTER) + 2;
        d.y += 6; Paint ln = new Paint(); ln.setColor(0xFF047857); ln.setStrokeWidth(1.5f); d.c.drawLine(M, d.y, d.w - M, d.y, ln); d.y += 8;
        d.y += draw(d.c, title, paint(15, true, Color.BLACK), M, d.y, w, CENTER);
        if (sub != null && !sub.isEmpty()) d.y += draw(d.c, sub, paint(10, false, 0xFF475569), M, d.y + 2, w, CENTER) + 2;
        d.y += 10;
    }
    static String[] nonEmpty(String... a) { List<String> l = new ArrayList<>(); for (String s : a) if (s != null && !s.trim().isEmpty()) l.add(s.trim()); return l.toArray(new String[0]); }
    static void footer(Doc d) {
        draw(d.c, "صفحة " + d.no + "  •  " + Ui.dt(java.time.LocalDateTime.now()) + "  •  MarketPro", paint(8, false, 0xFF94A3B8), M, d.h - M + 8, d.w - 2 * M, CENTER);
    }

    /** جدول تقرير كامل مع تكرار رأس الجدول في كل صفحة */
    static void table(Doc d, String title, String sub, String summary, String[] cols, List<String[]> rows) {
        TextPaint hp = paint(9.5f, true, Color.WHITE), bp = paint(9.5f, false, Color.BLACK), tp = paint(9.5f, true, 0xFF065F46);
        int n = cols.length, avail = d.w - 2 * M; float[] want = new float[n];
        for (int c = 0; c < n; c++) { want[c] = Math.min(160, hp.measureText(cols[c]) + 10);
            for (String[] r : rows) if (c < r.length && r[c] != null) want[c] = Math.max(want[c], Math.min(170, bp.measureText(r[c]) + 10)); }
        float sum = 0; for (float v : want) sum += v; int[] cw = new int[n]; for (int c = 0; c < n; c++) cw[c] = (int) (want[c] * avail / sum);
        d.newPage(); header(d, title, sub);
        if (summary != null && !summary.isEmpty()) d.y += draw(d.c, summary.replace("  |  ", "   •   "), paint(10, true, 0xFF047857), M, d.y, avail, R) + 8;
        Paint bg = new Paint(), grid = new Paint(); grid.setColor(0xFFCBD5E1); grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(0.5f);
        Runnable[] head = new Runnable[1];
        head[0] = () -> { int hh = rowH(cols, cw, hp); bg.setColor(0xFF047857); d.c.drawRect(M, d.y, d.w - M, d.y + hh, bg); drawRow(d, cols, cw, hp, hh); d.y += hh; };
        head[0].run();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i); boolean total = String.join("", r).contains("الإجمالي") || (r.length > 0 && r[0] != null && r[0].startsWith("المجموع"));
            TextPaint p = total ? tp : bp; int rh = rowH(r, cw, p);
            if (d.y + rh > d.h - M - 14) { footer(d); d.newPage(); head[0].run(); }
            if (total || i % 2 == 1) { bg.setColor(total ? 0xFFD1FAE5 : 0xFFF1F5F9); d.c.drawRect(M, d.y, d.w - M, d.y + rh, bg); }
            drawRow(d, r, cw, p, rh); d.c.drawLine(M, d.y + rh, d.w - M, d.y + rh, grid); d.y += rh;
        }
        footer(d);
    }
    static int rowH(String[] r, int[] cw, TextPaint p) { int h = 0; for (int c = 0; c < cw.length; c++) h = Math.max(h, layout(c < r.length ? r[c] : "", p, cw[c] - 6, CENTER).getHeight()); return h + 8; }
    /** الأعمدة من اليمين إلى اليسار */
    static void drawRow(Doc d, String[] r, int[] cw, TextPaint p, int rh) {
        int x = d.w - M;
        for (int c = 0; c < cw.length; c++) { x -= cw[c]; draw(d.c, c < r.length ? r[c] : "", p, x + 3, d.y + 4, cw[c] - 6, CENTER); }
    }

    public static void shareTable(Activity a, String title, String period, ReportApi.Table t) {
        Ux.run(a, () -> { try {
            Doc d = new Doc(t.cols.length > 6); table(d, title, period, t.summary, t.cols, t.rows);
            PdfViewActivity.open(a, d.save(a, title), title);
        } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); } });
    }

    /** فاتورة A4 */
    public static void invoice(Activity a, Invoice inv) {
        Ux.run(a, () -> { try {
            String party = inv.partyId == 0 ? (inv.partyName == null || inv.partyName.isEmpty() ? "نقدي" : inv.partyName) : Store.db.partyName(inv.partyId);
            List<String[]> rows = new ArrayList<>(); int i = 0;
            for (InvLine l : inv.lines) rows.add(new String[]{String.valueOf(++i), l.name, Ui.q(l.qty) + " " + l.unit, Ui.m(l.price), l.discount > 0 ? Ui.m(l.discount) : "", Ui.m(l.total())});
            rows.add(new String[]{"", "المجموع", "", "", "", Ui.m(inv.subtotal)});
            if (inv.discount > 0) rows.add(new String[]{"", "خصم الفاتورة", "", "", "", Ui.m(inv.discount)});
            if (inv.tax > 0) rows.add(new String[]{"", "الضريبة", "", "", "", Ui.m(inv.tax)});
            rows.add(new String[]{"", "الإجمالي", "", "", "", Ui.m(inv.total)});
            rows.add(new String[]{"", "المدفوع", "", "", "", Ui.m(inv.paid)});
            if (inv.total - inv.paid > 0.0005) rows.add(new String[]{"", "المتبقي", "", "", "", Ui.m(inv.total - inv.paid)});
            String sub = "رقم " + inv.no + "  •  " + Ui.dt(inv.date) + "  •  " + party + (inv.user == null || inv.user.isEmpty() ? "" : "  •  الكاشير: " + inv.user);
            Doc d = new Doc(false);
            table(d, inv.type.title + (inv.cancelled ? " (ملغاة)" : ""), sub, "", new String[]{"#", "الصنف", "الكمية", "السعر", "الخصم", "المجموع"}, rows);
            String foot = Store.db.settings.getOrDefault("footer", "");
            if (!foot.isEmpty() && d.y < d.h - M - 40) d.y += draw(d.c, foot, paint(11, false, 0xFF475569), M, d.y + 14, d.w - 2 * M, CENTER) + 14;
            if (inv.einvQr != null && !inv.einvQr.isEmpty()) { android.graphics.Bitmap q = Codes.qr(inv.einvQr, 300);
                if (q != null) { if (d.y + 130 > d.h - M) { footer(d); d.newPage(); } d.c.drawBitmap(q, null, new android.graphics.Rect(d.w / 2 - 55, d.y + 10, d.w / 2 + 55, d.y + 120), null); } }
            PdfViewActivity.open(a, d.save(a, inv.type.title + "_" + inv.no), inv.type.title + " رقم " + inv.no);
        } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); } });
    }
}
