package com.marketpro.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** معاينة ملف PDF داخل البرنامج مع الطباعة والمشاركة */
public class PdfViewActivity extends Activity {
    File file; float zoom = 1f; LinearLayout pages; String title;

    public static void open(Context c, File f, String title) {
        c.startActivity(new Intent(c, PdfViewActivity.class).putExtra("path", f.getAbsolutePath()).putExtra("title", title));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        file = new File(getIntent().getStringExtra("path")); title = getIntent().getStringExtra("title"); if (title == null) title = "معاينة PDF";
        LinearLayout body = Ux.screen(this, title, false);
        LinearLayout bar = Ux.row(this);
        bar.addView(Ux.button(this, "طباعة", Ux.PRIMARY, v -> print()), Ux.weight(1));
        bar.addView(gap()); bar.addView(Ux.button(this, "مشاركة", Ux.BLUE, v -> Share.share(this, file, "application/pdf")), Ux.weight(1));
        bar.addView(gap()); bar.addView(Ux.button(this, "＋", Ux.SLATE, v -> { zoom = Math.min(3f, zoom + 0.5f); render(); }), Ux.weight(0.45f));
        bar.addView(gap()); bar.addView(Ux.button(this, "－", Ux.SLATE, v -> { zoom = Math.max(1f, zoom - 0.5f); render(); }), Ux.weight(0.45f));
        body.addView(bar);
        ScrollView sv = new ScrollView(this); android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        pages = new LinearLayout(this); pages.setOrientation(LinearLayout.VERTICAL); pages.setBackgroundColor(0xFF9CA3AF); int p = Ux.dp(this, 8); pages.setPadding(p, p, p, p);
        hs.addView(pages); sv.addView(hs); sv.setFillViewport(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 8); body.addView(sv, lp);
        body.post(this::render);
    }
    View gap() { View g = new View(this); g.setLayoutParams(new LinearLayout.LayoutParams(Ux.dp(this, 6), 1)); return g; }

    void render() {
        pages.removeAllViews();
        int width = (int) ((getResources().getDisplayMetrics().widthPixels - Ux.dp(this, 40)) * zoom);
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer r = new PdfRenderer(fd)) {
            for (int i = 0; i < r.getPageCount(); i++) {
                try (PdfRenderer.Page pg = r.openPage(i)) {
                    int h = (int) ((long) width * pg.getHeight() / pg.getWidth());
                    Bitmap bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888); bmp.eraseColor(Color.WHITE);
                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    ImageView iv = new ImageView(this); iv.setImageBitmap(bmp); iv.setElevation(Ux.dp(this, 3));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, h); lp.bottomMargin = Ux.dp(this, 8); pages.addView(iv, lp);
                }
            }
            if (r.getPageCount() > 1) { TextView t = Ux.text(this, r.getPageCount() + " صفحات", 12, true, Color.WHITE); pages.addView(t); }
        } catch (Exception e) { Ux.alert(this, "تعذّر عرض الملف: " + e.getMessage()); }
    }

    /** طباعة عبر خدمة الطباعة في أندرويد (طابعة شبكة / واي فاي / حفظ PDF) */
    void print() {
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        pm.print(title, new PrintDocumentAdapter() {
            @Override public void onLayout(PrintAttributes o, PrintAttributes n, CancellationSignal c, LayoutResultCallback cb, Bundle extras) {
                if (c.isCanceled()) { cb.onLayoutCancelled(); return; }
                cb.onLayoutFinished(new PrintDocumentInfo.Builder(file.getName()).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true);
            }
            @Override public void onWrite(PageRange[] pr, ParcelFileDescriptor dest, CancellationSignal c, WriteResultCallback cb) {
                try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(dest.getFileDescriptor())) {
                    byte[] buf = new byte[16384]; int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    cb.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                } catch (Exception e) { cb.onWriteFailed(e.getMessage()); }
            }
        }, null);
    }
}
