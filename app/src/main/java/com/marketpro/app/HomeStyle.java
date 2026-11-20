package com.marketpro.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import com.market.Store;

/** أشكال الواجهة الرئيسية الثمانية: فاتح، وداكن، ومتدرج. تُطبَّق على الشاشة الرئيسية فقط ولا تغيّر ثيم بقية الشاشات */
public final class HomeStyle {
    public final String name;
    public final boolean dark;            // نصوص بيضاء فوق خلفية داكنة أو متدرجة
    public final int bgTop, bgBot;        // تدرّج الخلفية
    public final int headTop, headBot;    // تدرّج الترويسة
    public final int card, stroke;        // البطاقات
    public final int text, muted;         // النصوص
    public final int quick;               // شريط الروابط السريعة

    private HomeStyle(String name, boolean dark, int bgTop, int bgBot, int headTop, int headBot,
                      int card, int stroke, int text, int muted, int quick) {
        this.name = name; this.dark = dark; this.bgTop = bgTop; this.bgBot = bgBot; this.headTop = headTop; this.headBot = headBot;
        this.card = card; this.stroke = stroke; this.text = text; this.muted = muted; this.quick = quick;
    }

    public static final HomeStyle[] ALL = {
        // 0 فاتح نظيف
        new HomeStyle("فاتح نظيف", false, 0xFFF4F6FB, 0xFFF4F6FB, 0xFF1E1B4B, 0xFF312E81,
                0xFFFFFFFF, 0xFFE3E7F1, 0xFF111827, 0xFF6B7280, 0x33312E81),
        // 1 داكن ليلي
        new HomeStyle("داكن ليلي", true, 0xFF0F1626, 0xFF0F1626, 0xFF0A1120, 0xFF1E2A44,
                0xFF1B2436, 0xFF33415C, 0xFFF3F6FB, 0xFF9FB0C6, 0x330A1120),
        // 2 متدرج بنفسجي
        new HomeStyle("متدرج بنفسجي", true, 0xFF4F46E5, 0xFF7C3AED, 0x66000000, 0x33000000,
                0x2EFFFFFF, 0x59FFFFFF, 0xFFFFFFFF, 0xFFE0E7FF, 0x33000000),
        // 3 متدرج غروب
        new HomeStyle("متدرج غروب", true, 0xFFF97316, 0xFFE11D48, 0x66000000, 0x33000000,
                0x2EFFFFFF, 0x59FFFFFF, 0xFFFFFFFF, 0xFFFFE4E6, 0x33000000),
        // 4 متدرج أخضر منعش
        new HomeStyle("متدرج أخضر", true, 0xFF10B981, 0xFF0D9488, 0x66000000, 0x33000000,
                0x2EFFFFFF, 0x59FFFFFF, 0xFFFFFFFF, 0xFFD1FAE5, 0x33000000),
        // 5 زجاجي فاتح
        new HomeStyle("زجاجي فاتح", false, 0xFFE0E7FF, 0xFFFCE7F3, 0xFF4F46E5, 0xFF7C3AED,
                0xB3FFFFFF, 0x66FFFFFF, 0xFF1F1D4D, 0xFF66709A, 0x26312E81),
        // 6 داكن متدرج
        new HomeStyle("داكن متدرج", true, 0xFF1E1B4B, 0xFF0B1020, 0xFF0B1020, 0xFF1E1B4B,
                0xFF1E293B, 0x33FFFFFF, 0xFFE2E8F0, 0xFF94A3B8, 0x330B1020),
        // 7 أسود أنيق
        new HomeStyle("أسود أنيق", true, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF18181B,
                0xFF0A0A0A, 0xFF262626, 0xFFFFFFFF, 0xFF9CA3AF, 0xFF141414),
    };

    public static int index() {
        try { int i = Integer.parseInt(Store.db.get("homeStyle", "0")); return i < 0 || i >= ALL.length ? 0 : i; }
        catch (Exception e) { return 0; }
    }

    public static HomeStyle current() { return ALL[index()]; }

    public static void select(int i) { Store.db.set("homeStyle", String.valueOf(i)); Store.save(); }

    /** خلفية الشاشة بتدرّج عمودي */
    public GradientDrawable bg() { return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgBot}); }

    /** الترويسة بتدرّج أفقي */
    public GradientDrawable head() { return new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{headTop, headBot}); }

    /** بطاقة مدوّرة بحواف خفيفة */
    public GradientDrawable card(Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(card); g.setCornerRadius(Ux.dp(c, 14)); g.setStroke(Ux.dp(c, 1), stroke); return g;
    }
}
