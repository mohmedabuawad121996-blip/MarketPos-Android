package com.marketpro.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import com.market.Model.InvLine;
import com.market.Model.Invoice;
import com.market.Store;
import com.market.Ui;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** طباعة إيصال حراري عبر بلوتوث (ESC/POS) — يُرسم الإيصال كصورة فيظهر النص العربي سليماً على أي طابعة */
public final class Printer {
    private Printer() {}
    static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static String mac() { return Store.db.get("btPrinter", ""); }
    public static String name() { return Store.db.get("btPrinterName", ""); }
    public static int dots() { return "58".equals(Store.db.get("paper", "80")) ? 384 : 576; }
    public static boolean auto() { return Store.db.getB("autoPrint", false); }

    static boolean perm(Activity a) {
        if (Build.VERSION.SDK_INT >= 31 && a.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 77);
            Ux.toast(a, "اسمح بصلاحية البلوتوث ثم أعد المحاولة"); return false;
        }
        return true;
    }

    /** اختيار طابعة من الأجهزة المقترنة */
    @SuppressLint("MissingPermission")
    public static void pick(Activity a, Runnable done) {
        if (!perm(a)) return;
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) { Ux.alert(a, "هذا الجهاز لا يدعم البلوتوث"); return; }
        if (!ad.isEnabled()) { Ux.alert(a, "شغّل البلوتوث أولاً، واقرن الطابعة من إعدادات الهاتف"); return; }
        List<BluetoothDevice> ds = new ArrayList<>(ad.getBondedDevices()); List<String> n = new ArrayList<>();
        for (BluetoothDevice d : ds) n.add((d.getName() == null ? "جهاز" : d.getName()) + "  (" + d.getAddress() + ")");
        if (ds.isEmpty()) { Ux.alert(a, "لا توجد أجهزة مقترنة. اقرن الطابعة من إعدادات البلوتوث في الهاتف ثم عد هنا"); return; }
        Ux.choose(a, "اختر الطابعة", n, i -> { Store.db.set("btPrinter", ds.get(i).getAddress()); Store.db.set("btPrinterName", ds.get(i).getName() == null ? "" : ds.get(i).getName()); Store.save();
            Ux.toast(a, "تم اختيار الطابعة"); if (done != null) done.run(); });
    }

    public static void printInvoice(Activity a, Invoice inv) {
        if (mac().isEmpty()) { Ux.confirm(a, "لم تُحدَّد طابعة بلوتوث. اختيارها الآن؟", () -> pick(a, () -> printInvoice(a, inv))); return; }
        int copies = (int) Math.max(1, Math.min(5, Store.db.getD("copies", 1)));
        print(a, receipt(inv), copies);
    }
    public static void printText(Activity a, String title, String body) {
        if (mac().isEmpty()) { Ux.confirm(a, "لم تُحدَّد طابعة بلوتوث. اختيارها الآن؟", () -> pick(a, () -> printText(a, title, body))); return; }
        Doc d = new Doc(dots()); d.center(title, 30, true); d.line(); d.text(body, 22, false); d.feed(40); print(a, d.bitmap(), 1);
    }

    @SuppressLint("MissingPermission")
    static void print(Activity a, Bitmap bmp, int copies) {
        if (!perm(a)) return;
        android.app.ProgressDialog pd = android.app.ProgressDialog.show(a, null, "جاري الطباعة…", true);
        new Thread(() -> {
            String err = null;
            BluetoothSocket s = null;
            try {
                BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter(); ad.cancelDiscovery();
                BluetoothDevice dev = ad.getRemoteDevice(mac());
                s = dev.createRfcommSocketToServiceRecord(SPP);
                try { s.connect(); } catch (Exception e) { s.close(); s = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", int.class).invoke(dev, 1); s.connect(); }
                OutputStream o = s.getOutputStream();
                byte[] data = raster(bmp);
                for (int c = 0; c < copies; c++) {
                    o.write(new byte[]{0x1B, 0x40});
                    for (int i = 0; i < data.length; i += 1024) { o.write(data, i, Math.min(1024, data.length - i)); o.flush(); Thread.sleep(15); }
                    o.write(new byte[]{0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00}); // تغذية وقص
                    o.flush();
                }
                Thread.sleep(400);
            } catch (Exception e) { err = "تعذّرت الطباعة: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "\nتأكد أن الطابعة مشغّلة وقريبة ومقترنة بالهاتف."; }
            finally { try { if (s != null) s.close(); } catch (Exception ignored) {} }
            String fe = err;
            a.runOnUiThread(() -> { try { pd.dismiss(); } catch (Exception ignored) {} if (fe != null) Ux.alert(a, fe); });
        }).start();
    }

    /** صورة ← أمر GS v 0 النقطي */
    static byte[] raster(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), bw = (w + 7) / 8;
        byte[] out = new byte[8 + bw * h];
        out[0] = 0x1D; out[1] = 0x76; out[2] = 0x30; out[3] = 0; out[4] = (byte) (bw & 0xFF); out[5] = (byte) (bw >> 8); out[6] = (byte) (h & 0xFF); out[7] = (byte) (h >> 8);
        int[] px = new int[w];
        for (int y = 0; y < h; y++) { b.getPixels(px, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) { int c = px[x]; int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
                if (Color.alpha(c) > 128 && lum < 140) out[8 + y * bw + x / 8] |= (byte) (0x80 >> (x % 8)); } }
        return out;
    }

    /** رسم إيصال على صورة بعرض الطابعة */
    static final class Doc {
        final int w; final List<Object[]> ops = new ArrayList<>(); int h = 8;
        Doc(int w) { this.w = w; }
        TextPaint p(float size, boolean bold) { TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG); t.setColor(Color.BLACK); t.setTextSize(size); t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); return t; }
        StaticLayout lay(String s, TextPaint t, int width, Layout.Alignment al) { return StaticLayout.Builder.obtain(s, 0, s.length(), t, Math.max(10, width)).setAlignment(al).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).build(); }
        void add(StaticLayout l, int x) { ops.add(new Object[]{l, x, h}); h += l.getHeight(); }
        void center(String s, float size, boolean bold) { if (s == null || s.isEmpty()) return; add(lay(s, p(size, bold), w, Layout.Alignment.ALIGN_CENTER), 0); }
        void text(String s, float size, boolean bold) { if (s == null || s.isEmpty()) return; add(lay(s, p(size, bold), w, Layout.Alignment.ALIGN_NORMAL), 0); }
        /** صف بعمودين: النص يميناً والقيمة يساراً */
        void pair(String right, String left, float size, boolean bold) {
            TextPaint t = p(size, bold); StaticLayout r = lay(right, t, w * 62 / 100, Layout.Alignment.ALIGN_NORMAL), l = lay(left, t, w * 38 / 100, Layout.Alignment.ALIGN_OPPOSITE);
            ops.add(new Object[]{r, w * 38 / 100, h}); ops.add(new Object[]{l, 0, h}); h += Math.max(r.getHeight(), l.getHeight());
        }
        void line() { ops.add(new Object[]{"line", 0, h + 4}); h += 10; }
        void feed(int px) { h += px; }
        void image(Bitmap b) { ops.add(new Object[]{b, (w - b.getWidth()) / 2, h}); h += b.getHeight() + 4; }
        Bitmap bitmap() {
            Bitmap b = Bitmap.createBitmap(w, h + 10, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); c.drawColor(Color.WHITE);
            Paint ln = new Paint(); ln.setColor(Color.BLACK); ln.setStrokeWidth(2);
            for (Object[] o : ops) {
                int x = (int) o[1], y = (int) o[2];
                if ("line".equals(o[0])) { for (int i = 0; i < w; i += 10) c.drawLine(i, y, i + 5, y, ln); }
                else if (o[0] instanceof Bitmap) c.drawBitmap((Bitmap) o[0], x, y, null);
                else { c.save(); c.translate(x, y); ((StaticLayout) o[0]).draw(c); c.restore(); }
            }
            return b;
        }
    }

    public static Bitmap receipt(Invoice inv) {
        boolean small = dots() < 500; float fs = small ? 20 : 24, big = small ? 30 : 36;
        Doc d = new Doc(dots());
        d.center(Store.db.get("storeName", ""), big, true);
        d.center(Store.db.get("address", ""), fs - 2, false);
        d.center(Store.db.get("phone", ""), fs - 2, false);
        String tn = Store.db.get("taxNo", ""); if (!tn.isEmpty()) d.center("الرقم الضريبي: " + tn, fs - 2, false);
        d.line();
        d.center(inv.type.title + " رقم " + inv.no, fs + 4, true);
        d.pair("التاريخ: " + Ui.dt(inv.date), inv.user == null ? "" : inv.user, fs - 2, false);
        if (inv.partyId != 0) d.text("العميل: " + Store.db.partyName(inv.partyId), fs, false);
        d.line();
        for (InvLine l : inv.lines) {
            d.text(l.name, fs, true);
            d.pair(Ui.q(l.qty) + " " + l.unit + " × " + Ui.m(l.price) + (l.discount > 0 ? "  خصم " + Ui.m(l.discount) : ""), Ui.m(l.total()), fs, false);
            if (l.offer != null && !l.offer.isEmpty()) d.text("  🏷 " + l.offer, fs - 4, false);
        }
        d.line();
        d.pair("المجموع", Ui.m(inv.subtotal), fs, false);
        if (inv.discount > 0) d.pair("الخصم", Ui.m(inv.discount), fs, false);
        if (inv.tax > 0) d.pair("الضريبة", Ui.m(inv.tax), fs, false);
        d.pair("الإجمالي", Ui.m(inv.total) + " " + Store.db.get("currency", ""), fs + 6, true);
        if (!inv.type.noEffect()) {
            d.pair("المدفوع", Ui.m(inv.paid), fs, false);
            if (inv.tendered > inv.paid + 0.0005) d.pair("الباقي للزبون", Ui.m(inv.tendered - inv.paid), fs, false);
            if (inv.total - inv.paid > 0.0005) d.pair("المتبقي (آجل)", Ui.m(inv.total - inv.paid), fs, true);
        }
        if (inv.einvQr != null && !inv.einvQr.isEmpty()) { Bitmap q = Codes.qr(inv.einvQr, small ? 220 : 280); if (q != null) { d.feed(6); d.image(q); d.center("الفوترة الوطنية", fs - 4, false); } }
        d.line();
        for (String f : Store.db.get("footer", "").split("\\r?\\n")) d.center(f, fs - 2, false);
        d.feed(30);
        return d.bitmap();
    }
}
