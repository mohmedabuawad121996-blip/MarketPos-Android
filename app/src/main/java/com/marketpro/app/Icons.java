package com.marketpro.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** نفس أيقونات برنامج الكمبيوتر الخطية المرسومة بالمتجهات على شبكة 24×24 */
public final class Icons {
    private Icons() {}
    static final Map<String, List<Path>> CACHE = new HashMap<>();

    public static Drawable get(String name, int sizePx, int color) { return new Vec(name, sizePx, color); }

    static final class Vec extends Drawable {
        final String name; final int size; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Vec(String n, int s, int c) { name = n; size = s; p.setColor(c); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); setBounds(0, 0, s, s); }
        @Override public void draw(Canvas c) {
            c.save(); c.translate(getBounds().left, getBounds().top); float k = getBounds().width() / 24f; c.scale(k, k);
            for (Path path : shapes(name)) c.drawPath(path, p);
            c.restore();
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static Path L(float a, float b, float c, float d) { Path p = new Path(); p.moveTo(a, b); p.lineTo(c, d); return p; }
    static Path C(float cx, float cy, float r) { Path p = new Path(); p.addCircle(cx, cy, r, Path.Direction.CW); return p; }
    static Path R(float x, float y, float w, float h, float r) { Path p = new Path(); p.addRoundRect(new RectF(x, y, x + w, y + h), r / 2, r / 2, Path.Direction.CW); return p; }
    static Path E(float x, float y, float w, float h) { Path p = new Path(); p.addOval(new RectF(x, y, x + w, y + h), Path.Direction.CW); return p; }
    static Path P(float... pts) { Path p = new Path(); p.moveTo(pts[0], pts[1]); for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]); return p; }
    static Path Pc(float... pts) { Path p = P(pts); p.close(); return p; }
    /** قوس بنفس اصطلاح Java2D: الزوايا عكس عقارب الساعة */
    static Path arc(float x, float y, float w, float h, float start, float ext) { Path p = new Path(); p.addArc(new RectF(x, y, x + w, y + h), -start, -ext); return p; }

    static synchronized List<Path> shapes(String n) {
        List<Path> s = CACHE.get(n); if (s != null) return s; s = new ArrayList<>();
        switch (n) {
            case "home": s.add(P(3, 11, 12, 3.5f, 21, 11)); s.add(P(5.5f, 9.5f, 5.5f, 20.5f, 18.5f, 20.5f, 18.5f, 9.5f)); s.add(P(10, 20.5f, 10, 14.5f, 14, 14.5f, 14, 20.5f)); break;
            case "cart": s.add(P(2, 3, 5, 3, 7.6f, 15, 18, 15, 21, 6.5f, 6, 6.5f)); s.add(C(9, 19.5f, 1.6f)); s.add(C(17, 19.5f, 1.6f)); break;
            case "box": s.add(Pc(12, 2.5f, 20.5f, 7, 20.5f, 17, 12, 21.5f, 3.5f, 17, 3.5f, 7)); s.add(P(3.5f, 7, 12, 11.5f, 20.5f, 7)); s.add(L(12, 11.5f, 12, 21.5f)); s.add(L(7.8f, 4.8f, 16.3f, 9.3f)); break;
            case "tag": s.add(Pc(3, 3, 11, 3, 21, 13, 13, 21, 3, 11)); s.add(C(7.5f, 7.5f, 1.4f)); break;
            case "users": s.add(C(9, 8, 3.6f)); s.add(arc(2.5f, 13, 13, 14, 0, 180)); s.add(arc(14, 4.5f, 6, 7, -90, 180)); s.add(P(17.5f, 14, 21.5f, 15.5f, 21.5f, 20)); break;
            case "user-plus": s.add(C(9, 8, 3.6f)); s.add(arc(2.5f, 13, 13, 14, 0, 180)); s.add(L(19, 8, 19, 14)); s.add(L(16, 11, 22, 11)); break;
            case "truck": s.add(R(1.5f, 5.5f, 13, 11, 2)); s.add(P(14.5f, 9, 18.5f, 9, 22, 12.5f, 22, 16.5f, 14.5f, 16.5f)); s.add(C(6, 18.5f, 2)); s.add(C(18, 18.5f, 2)); break;
            case "cash": s.add(R(2, 6, 20, 12, 3)); s.add(C(12, 12, 2.8f)); s.add(L(5.5f, 12, 6, 12)); s.add(L(18, 12, 18.5f, 12)); break;
            case "wallet": s.add(R(2.5f, 6.5f, 18.5f, 14, 3)); s.add(P(4.5f, 6.5f, 15.5f, 3.2f, 17, 6.5f)); s.add(R(14.5f, 11, 7.5f, 5, 2)); s.add(L(17.5f, 13.5f, 17.6f, 13.5f)); break;
            case "chart": s.add(P(3.5f, 3, 3.5f, 20.5f, 21, 20.5f)); s.add(L(8.5f, 16.5f, 8.5f, 12)); s.add(L(13, 16.5f, 13, 7.5f)); s.add(L(17.5f, 16.5f, 17.5f, 10.5f)); break;
            case "sliders": s.add(L(4, 6, 20, 6)); s.add(L(4, 12, 20, 12)); s.add(L(4, 18, 20, 18)); s.add(C(9, 6, 2)); s.add(C(15, 12, 2)); s.add(C(7, 18, 2)); break;
            case "gear": { s.add(C(12, 12, 3)); Path p = new Path(); int teeth = 8;
                for (int i = 0; i < teeth * 2; i++) { double a1 = Math.PI * 2 * i / (teeth * 2) - Math.PI / (teeth * 2) * 0.6, a2 = Math.PI * 2 * (i + 1) / (teeth * 2) - Math.PI / (teeth * 2) * 0.6; double r = i % 2 == 0 ? 9.2 : 7;
                    if (i == 0) p.moveTo((float) (12 + r * Math.cos(a1)), (float) (12 + r * Math.sin(a1))); else p.lineTo((float) (12 + r * Math.cos(a1)), (float) (12 + r * Math.sin(a1)));
                    p.lineTo((float) (12 + r * Math.cos(a2)), (float) (12 + r * Math.sin(a2))); }
                p.close(); s.add(p); break; }
            case "search": s.add(C(10.5f, 10.5f, 6.5f)); s.add(L(15.5f, 15.5f, 21, 21)); break;
            case "plus": s.add(L(12, 5, 12, 19)); s.add(L(5, 12, 19, 12)); break;
            case "print": s.add(P(6.5f, 8, 6.5f, 3, 17.5f, 3, 17.5f, 8)); s.add(R(3, 8, 18, 9, 3)); s.add(R(6.5f, 13.5f, 11, 7.5f, 1.5f)); break;
            case "save": s.add(Pc(4, 4, 16.5f, 4, 20, 7.5f, 20, 20, 4, 20)); s.add(P(8, 4, 8, 8.5f, 15, 8.5f, 15, 4)); s.add(R(7.5f, 13, 9, 7, 1)); break;
            case "trash": s.add(L(3.5f, 6.5f, 20.5f, 6.5f)); s.add(P(5.5f, 6.5f, 6.8f, 20.5f, 17.2f, 20.5f, 18.5f, 6.5f)); s.add(P(9, 6.5f, 9, 3.5f, 15, 3.5f, 15, 6.5f)); s.add(L(10, 10.5f, 10, 16.5f)); s.add(L(14, 10.5f, 14, 16.5f)); break;
            case "edit": s.add(Pc(4, 20, 4.5f, 15.8f, 15.5f, 4.8f, 19.2f, 8.5f, 8.2f, 19.5f)); s.add(L(13.2f, 7.1f, 16.9f, 10.8f)); break;
            case "export": s.add(Pc(6, 2.5f, 14, 2.5f, 19, 7.5f, 19, 21.5f, 6, 21.5f)); s.add(P(14, 2.5f, 14, 7.5f, 19, 7.5f)); s.add(L(12.5f, 10.5f, 12.5f, 18)); s.add(P(9.5f, 15, 12.5f, 18, 15.5f, 15)); break;
            case "import": s.add(Pc(6, 2.5f, 14, 2.5f, 19, 7.5f, 19, 21.5f, 6, 21.5f)); s.add(P(14, 2.5f, 14, 7.5f, 19, 7.5f)); s.add(L(12.5f, 18, 12.5f, 10.5f)); s.add(P(9.5f, 13.5f, 12.5f, 10.5f, 15.5f, 13.5f)); break;
            case "refresh": s.add(arc(4, 4, 16, 16, 60, 280)); s.add(P(19.5f, 3.5f, 17.6f, 6.6f, 14.2f, 5.4f)); break;
            case "x": s.add(L(6, 6, 18, 18)); s.add(L(18, 6, 6, 18)); break;
            case "check": s.add(P(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f)); break;
            case "ban": s.add(C(12, 12, 9)); s.add(L(5.6f, 18.4f, 18.4f, 5.6f)); break;
            case "undo": { s.add(P(9, 4.5f, 4.5f, 9, 9, 13.5f)); Path p = new Path(); p.moveTo(4.5f, 9); p.lineTo(14.5f, 9); p.cubicTo(18.5f, 9, 20.5f, 11.5f, 20.5f, 14.5f); p.cubicTo(20.5f, 17.5f, 18.5f, 20, 14.5f, 20); p.lineTo(10, 20); s.add(p); break; }
            case "doc": s.add(Pc(6, 2.5f, 14, 2.5f, 19, 7.5f, 19, 21.5f, 6, 21.5f)); s.add(P(14, 2.5f, 14, 7.5f, 19, 7.5f)); s.add(L(9, 12.5f, 16, 12.5f)); s.add(L(9, 16.5f, 16, 16.5f)); break;
            case "history": s.add(C(12, 12, 9)); s.add(L(12, 7, 12, 12)); s.add(L(12, 12, 15.5f, 14.5f)); break;
            case "barcode": for (float xx : new float[]{3.5f, 6.5f, 8.5f, 11.5f, 14, 16.5f, 18.5f, 20.5f}) s.add(L(xx, 5, xx, 19)); break;
            case "palette": s.add(arc(2.5f, 2.5f, 19, 19, 300, 300)); s.add(P(16.9f, 20.2f, 15.5f, 17.5f, 17.5f, 15.5f, 20.5f, 16.3f)); s.add(C(8, 10, 0.8f)); s.add(C(12, 7, 0.8f)); s.add(C(16, 9.5f, 0.8f)); s.add(C(8, 15, 0.8f)); break;
            case "font": s.add(P(4, 20, 10, 4, 16, 20)); s.add(L(6.2f, 14.5f, 13.8f, 14.5f)); s.add(L(17, 20, 21, 20)); break;
            case "key": s.add(C(7.5f, 15.5f, 4)); s.add(P(10.5f, 12.5f, 20, 3)); s.add(L(16.5f, 6.5f, 19, 9)); s.add(L(14, 9, 16, 11)); break;
            case "cloud": { Path p = new Path(); p.moveTo(7, 19); p.cubicTo(3.5f, 19, 2, 16.5f, 2, 14.5f); p.cubicTo(2, 12, 4, 10, 6.5f, 10); p.cubicTo(7.3f, 6.5f, 10, 4.5f, 13, 4.5f);
                p.cubicTo(17, 4.5f, 19.5f, 7.5f, 19.5f, 11); p.cubicTo(21.3f, 11.5f, 22.5f, 13, 22.5f, 15); p.cubicTo(22.5f, 17.3f, 20.8f, 19, 18.5f, 19); p.close(); s.add(p); s.add(L(12, 16.5f, 12, 10)); s.add(P(9.5f, 12.5f, 12, 10, 14.5f, 12.5f)); break; }
            case "link": s.add(R(2.5f, 8.5f, 10, 7, 7)); s.add(R(11.5f, 8.5f, 10, 7, 7)); s.add(L(8, 12, 16, 12)); break;
            case "database": s.add(E(4, 3, 16, 5)); s.add(P(4, 5.5f, 4, 18.5f)); s.add(P(20, 5.5f, 20, 18.5f)); s.add(arc(4, 16, 16, 5, 180, 180)); s.add(arc(4, 9.5f, 16, 5, 180, 180)); break;
            case "lock": s.add(R(4.5f, 10.5f, 15, 10.5f, 3)); s.add(arc(7.5f, 3, 9, 13, 0, 180)); s.add(L(7.5f, 9.5f, 7.5f, 10.5f)); s.add(L(16.5f, 9.5f, 16.5f, 10.5f)); break;
            case "calendar": s.add(R(3.5f, 5, 17, 16, 3)); s.add(L(3.5f, 10, 20.5f, 10)); s.add(L(8, 3, 8, 7)); s.add(L(16, 3, 16, 7)); break;
            case "list": s.add(L(9, 6, 20, 6)); s.add(L(9, 12, 20, 12)); s.add(L(9, 18, 20, 18)); s.add(L(4, 6, 4.2f, 6)); s.add(L(4, 12, 4.2f, 12)); s.add(L(4, 18, 4.2f, 18)); break;
            case "percent": s.add(L(19, 5, 5, 19)); s.add(C(7, 7, 2.3f)); s.add(C(17, 17, 2.3f)); break;
            case "power": s.add(L(12, 3, 12, 11)); s.add(arc(4, 5, 16, 16, 125, 290)); break;
            case "eye": { Path p = new Path(); p.moveTo(2, 12); p.cubicTo(5, 6.5f, 8.5f, 5, 12, 5); p.cubicTo(15.5f, 5, 19, 6.5f, 22, 12); p.cubicTo(19, 17.5f, 15.5f, 19, 12, 19); p.cubicTo(8.5f, 19, 5, 17.5f, 2, 12); p.close(); s.add(p); s.add(C(12, 12, 3)); break; }
            case "shield": s.add(Pc(12, 3, 19.5f, 5.5f, 19.5f, 11.5f, 12, 21, 4.5f, 11.5f, 4.5f, 5.5f)); s.add(P(8.5f, 12, 11, 14.5f, 15.5f, 9.5f)); break;
            case "login": s.add(R(3.5f, 4, 9, 16, 2.5f)); s.add(L(9.5f, 12, 19.5f, 12)); s.add(P(15.5f, 8, 19.5f, 12, 15.5f, 16)); break;
            case "logout": s.add(P(9, 4, 5, 4, 5, 20, 9, 20)); s.add(L(10, 12, 20, 12)); s.add(P(16, 8, 20, 12, 16, 16)); break;
            case "user": s.add(C(12, 8, 4)); s.add(arc(4, 14, 16, 14, 0, 180)); break;
            case "info": s.add(C(12, 12, 9)); s.add(L(12, 11, 12, 16.5f)); s.add(C(12, 7.8f, 0.5f)); break;
            case "menu": s.add(L(4, 6, 20, 6)); s.add(L(4, 12, 20, 12)); s.add(L(4, 18, 20, 18)); break;
            case "chevron-down": s.add(P(6, 9, 12, 15, 18, 9)); break;
            case "chevron-left": s.add(P(15, 6, 9, 12, 15, 18)); break;
            case "chevron-right": s.add(P(9, 6, 15, 12, 9, 18)); break;
            case "camera": s.add(R(2.5f, 6.5f, 19, 14, 3)); s.add(P(8, 6.5f, 9.5f, 3.5f, 14.5f, 3.5f, 16, 6.5f)); s.add(C(12, 13.5f, 3.6f)); break;
            case "share": s.add(C(18, 5.5f, 2.5f)); s.add(C(6, 12, 2.5f)); s.add(C(18, 18.5f, 2.5f)); s.add(L(8.3f, 10.8f, 15.7f, 6.7f)); s.add(L(8.3f, 13.2f, 15.7f, 17.3f)); break;
            default: s.add(C(12, 12, 8));
        }
        CACHE.put(n, s); return s;
    }

    /** اختيار أيقونة تلقائياً من نص الزر (نفس قاموس برنامج الكمبيوتر) */
    public static String forText(String t) {
        if (t == null) return null;
        String[][] map = {
                {"حفظ وطباعة", "print"}, {"طباعة", "print"}, {"PDF", "print"}, {"حفظ", "save"}, {"الدفع", "cash"}, {"اعتماد", "check"}, {"تطبيق", "check"}, {"اختيار", "check"},
                {"حذف", "trash"}, {"إلغاء", "ban"}, {"تعديل", "edit"}, {"إعادة تسمية", "edit"}, {"بحث", "search"}, {"تصدير", "export"}, {"استيراد", "import"}, {"Excel", "export"},
                {"تحديث", "refresh"}, {"إغلاق", "x"}, {"جديد", "plus"}, {"إضافة", "plus"}, {"+", "plus"}, {"مرتجع", "undo"}, {"إرجاع", "undo"}, {"كشف حساب", "doc"},
                {"عرض", "list"}, {"سند قبض", "cash"}, {"سند صرف", "cash"}, {"تسديد", "cash"}, {"نقدي", "cash"}, {"آجل", "calendar"}, {"إيداع", "wallet"}, {"سحب", "wallet"}, {"تحويل", "refresh"},
                {"نسخة", "cloud"}, {"استعادة", "undo"}, {"تشغيل", "power"}, {"إيقاف", "power"}, {"تالف", "ban"}, {"نسخ", "doc"}, {"مشاركة", "share"}, {"نص", "doc"}, {"واتساب", "share"},
                {"بيع", "cart"}, {"شراء", "box"}, {"سعر", "tag"}, {"التقارير", "chart"}, {"الرئيسية", "home"}, {"فحص", "search"}, {"إعادة احتساب", "refresh"}, {"تصفير", "trash"},
                {"إقفال", "lock"}, {"تفعيل", "key"}, {"الطابعة", "print"}, {"ملصق", "barcode"}, {"باركود", "barcode"}, {"المقاس", "sliders"}, {"تعليق", "history"}, {"المعلّقة", "history"},
                {"النقاط", "percent"}, {"من فاتورة", "doc"}, {"دليل", "list"}, {"قيد", "doc"}, {"المورد", "truck"}, {"العميل", "users"}, {"الفترة", "calendar"}, {"التصنيف", "tag"},
                {"الصنف", "box"}, {"المحفظة", "wallet"}, {"الصندوق", "wallet"}, {"المستودع", "box"}, {"الحساب", "doc"}, {"دخول", "login"}, {"اتصال", "user"}};
        for (String[] m : map) if (t.contains(m[0])) return m[1];
        return null;
    }
}
