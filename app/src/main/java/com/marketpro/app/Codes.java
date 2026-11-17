package com.marketpro.app;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

/** توليد صور الباركود ورمز QR */
public final class Codes {
    private Codes() {}
    public static Bitmap qr(String text, int size) {
        try { Map<EncodeHintType, Object> h = new EnumMap<>(EncodeHintType.class); h.put(EncodeHintType.CHARACTER_SET, "UTF-8"); h.put(EncodeHintType.MARGIN, 1);
            return bmp(new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, h)); } catch (Exception e) { return null; }
    }
    /** باركود EAN-13 إن كان الرقم صالحاً، وإلا Code128 */
    public static Bitmap barcode(String code, int w, int h) {
        try {
            BarcodeFormat f = code.matches("\\d{13}") ? BarcodeFormat.EAN_13 : code.matches("\\d{8}") ? BarcodeFormat.EAN_8 : BarcodeFormat.CODE_128;
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class); hints.put(EncodeHintType.MARGIN, 0);
            try { return bmp(new MultiFormatWriter().encode(code, f, w, h, hints)); }
            catch (Exception e) { return bmp(new MultiFormatWriter().encode(code, BarcodeFormat.CODE_128, w, h, hints)); }
        } catch (Exception e) { return null; }
    }
    static Bitmap bmp(BitMatrix m) {
        int w = m.getWidth(), h = m.getHeight(); int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }
}
