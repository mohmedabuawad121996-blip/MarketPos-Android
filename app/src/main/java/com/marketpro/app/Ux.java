package com.marketpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.market.Ops;

import java.util.ArrayList;
import java.util.List;

/** أدوات بناء الواجهة برمجياً بنفس ثيمات وألوان برنامج الكمبيوتر */
public final class Ux {
    private Ux() {}

    // ألوان الثيم الحالي (نفس ثيمات برنامج الكمبيوتر وبنفس مفتاح الإعداد theme)
    public static int PRIMARY, DARK, HEADER, SIDEBAR, SIDE_ITEM, SIDE_TEXT, BG, CARD, TEXT, MUTED, ROW, ROW_ALT, HEAD, SELECT, BORDER;
    public static boolean DARK_MODE;
    public static final int GREEN = 0xFF16A34A, RED = 0xFFDC2626, ORANGE = 0xFFEA8A1A, GRAY = 0xFF64748B, PURPLE = 0xFF7C3AED, TEAL = 0xFF0D9488,
            GOLD = 0xFFB7791F, SLATE = 0xFF475569, BLUE = 0xFF2563EB;
    /** مقياس حجم الخط */
    public static float FS = 1f;
    public static final String[] THEMES = {"أزرق عصري", "داكن ليلي", "داكن فحمي", "فيروزي", "أخضر طبيعي", "بنفسجي", "وردي دافئ", "رملي فاتح", "رمادي احترافي", "زجاجي"};
    static { theme(THEMES[0]); }

    static void theme(String name) {
        DARK_MODE = false;
        switch (name) {
            case "داكن ليلي": DARK_MODE = true; set(0x2563EB, 0xBFDBFE, 0x0A1120, 0x080D18, 0x1C2942, 0xE8EEF6, 0x0F1626, 0x1B2436, 0xF3F6FB, 0x9FB0C6, 0x1B2436, 0x222E44, 0x2C3A54, 0x2E5BB8, 0x33415C); break;
            case "داكن فحمي": DARK_MODE = true; set(0x0369A1, 0x7DD3FC, 0x000000, 0x0A0A0B, 0x1A1A1D, 0xE7E7EA, 0x0B0B0D, 0x17181B, 0xFAFAFA, 0xA1A1AA, 0x17181B, 0x202126, 0x27282E, 0x0B4A6F, 0x2B2C32); break;
            case "فيروزي": set(0x0F766E, 0x134E4A, 0x115E59, 0x042F2E, 0x134E4A, 0xCCFBF1, 0xF0FDFA, 0xFFFFFF, 0x0F2A27, 0x5B7A76, 0xFFFFFF, 0xF1FBF9, 0xCCFBF1, 0x99F6E4, 0xCBEEEA); break;
            case "وردي دافئ": set(0xE11D48, 0x881337, 0x9F1239, 0x4C0519, 0x881337, 0xFFE4E6, 0xFFF1F2, 0xFFFFFF, 0x2B1216, 0x8A5B62, 0xFFFFFF, 0xFFF6F7, 0xFFE4E6, 0xFECDD3, 0xF6D9DD); break;
            case "أخضر طبيعي": set(0x047857, 0x064E3B, 0x065F46, 0x022C22, 0x064E3B, 0xD1FAE5, 0xF0FDF4, 0xFFFFFF, 0x14281D, 0x5B7065, 0xFFFFFF, 0xF3FAF6, 0xD1FAE5, 0xBBF7D0, 0xCFE8DA); break;
            case "بنفسجي": set(0x7C3AED, 0x3B0764, 0x4C1D95, 0x2E1065, 0x3B0764, 0xEDE9FE, 0xF5F3FF, 0xFFFFFF, 0x1E1B2E, 0x6B6880, 0xFFFFFF, 0xF8F6FF, 0xEDE9FE, 0xDDD6FE, 0xE4DEFA); break;
            case "رملي فاتح": set(0xB45309, 0x5B3A1A, 0x7C4A1E, 0x3F2A17, 0x5B3A1A, 0xFDF2E3, 0xFBF7F0, 0xFFFFFF, 0x2B2118, 0x7A6A58, 0xFFFFFF, 0xFCF8F2, 0xF5E6CE, 0xFDE3B8, 0xEDE0CB); break;
            case "رمادي احترافي": set(0x334155, 0x0F172A, 0x1E293B, 0x0F172A, 0x1E293B, 0xE2E8F0, 0xF8FAFC, 0xFFFFFF, 0x0F172A, 0x64748B, 0xFFFFFF, 0xF8FAFC, 0xE2E8F0, 0xCBD5E1, 0xE2E8F0); break;
            case "زجاجي": set(0x6D5BD0, 0x3C2F8A, 0x5B6FD6, 0x2B2F6B, 0x3D4390, 0xF2F4FF, 0xE8EEFB, 0xFFFFFF, 0x1F1D4D, 0x66709A, 0xFFFFFF, 0xF4F6FD, 0xDDE3F7, 0xCFD8F5, 0xD3DBF2); break;
            default: set(0x4F46E5, 0x312E81, 0x1E1B4B, 0x0B1020, 0x1E2A44, 0xE2E8F0, 0xF4F6FC, 0xFFFFFF, 0x111827, 0x6B7280, 0xFFFFFF, 0xF8F9FE, 0xE8EAFB, 0xE0E7FF, 0xE3E7F1);
        }
    }
    static void set(int primary, int dark, int header, int sidebar, int sideItem, int sideText, int bg, int card, int text, int muted, int row, int rowAlt, int head, int select, int border) {
        PRIMARY = 0xFF000000 | primary; DARK = 0xFF000000 | dark; HEADER = 0xFF000000 | header; SIDEBAR = 0xFF000000 | sidebar; SIDE_ITEM = 0xFF000000 | sideItem;
        SIDE_TEXT = 0xFF000000 | sideText; BG = 0xFF000000 | bg; CARD = 0xFF000000 | card; TEXT = 0xFF000000 | text; MUTED = 0xFF000000 | muted; ROW = 0xFF000000 | row;
        ROW_ALT = 0xFF000000 | rowAlt; HEAD = 0xFF000000 | head; SELECT = 0xFF000000 | select; BORDER = 0xFF000000 | border;
    }
    public static String themeName() { return com.market.Store.db == null ? THEMES[0] : com.market.Store.db.get("theme", THEMES[0]); }
    public static void applyTheme() {
        theme(themeName());
        FS = (float) Math.max(0.8, Math.min(1.4, com.market.Store.db.getD("phoneFont", 1)));
    }
    /** تفتيح أو تغميق لون (نفس Ui.shade في برنامج الكمبيوتر) */
    public static int shade(int c, double f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (f >= 0) { r += (int) ((255 - r) * f); g += (int) ((255 - g) * f); b += (int) ((255 - b) * f); }
        else { r = (int) (r * (1 + f)); g = (int) (g * (1 + f)); b = (int) (b * (1 + f)); }
        return 0xFF000000 | (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, radiusDp)); return g;
    }
    public static GradientDrawable outline(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = round(fill, radiusDp, c); g.setStroke(dp(c, 1), stroke); return g;
    }

    /** هيكل الشاشة: شريط علوي + محتوى + لائحة جانبية (مثل برنامج الكمبيوتر). يرجع الحاوية التي تُضاف إليها العناصر */
    public static LinearLayout screen(Activity a, String title, boolean scroll) {
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        androidx.drawerlayout.widget.DrawerLayout dl = Sidebar.wrap(a, root, title);
        root.addView(header(a, title, !(a instanceof HomeActivity), dl));
        LinearLayout body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 12); body.setPadding(p, p, p, p);
        if (scroll) {
            ScrollView sv = new ScrollView(a); sv.setFillViewport(true); sv.addView(body);
            root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        } else root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        a.setContentView(dl);
        a.getWindow().setStatusBarColor(HEADER);
        return body;
    }

    public static View header(Activity a, String title, boolean back) { return header(a, title, back, null); }
    /** الشريط العلوي بتدرّج لون الترويسة: ☰ اللائحة الجانبية، العنوان، الرئيسية، رجوع */
    public static View header(Activity a, String title, boolean back, androidx.drawerlayout.widget.DrawerLayout dl) {
        LinearLayout h = new LinearLayout(a); h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{HEADER, shade(HEADER, DARK_MODE ? 0.08 : 0.18)}); h.setBackground(g);
        int p = dp(a, 10); h.setPadding(p, p, p, p);
        if (dl != null) h.addView(iconBtn(a, "menu", Color.WHITE, v -> dl.openDrawer(androidx.core.view.GravityCompat.START)));
        TextView t = text(a, title, 18, true, Color.WHITE); t.setPadding(dp(a, 8), 0, dp(a, 8), 0); t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (back) {
            h.addView(iconBtn(a, "home", Color.WHITE, v -> { a.startActivity(new android.content.Intent(a, HomeActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)); a.finish(); }));
            h.addView(iconBtn(a, "chevron-left", Color.WHITE, v -> a.finish()));
        }
        return h;
    }
    /** زر أيقونة شفاف */
    public static View iconBtn(Context c, String icon, int color, View.OnClickListener l) {
        android.widget.ImageView v = new android.widget.ImageView(c); v.setImageDrawable(Icons.get(icon, dp(c, 24), color));
        int p = dp(c, 8); v.setPadding(p, p, p, p); v.setBackground(round(0x00FFFFFF, 8, c)); v.setOnClickListener(l);
        android.util.TypedValue tv = new android.util.TypedValue(); c.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true); v.setForeground(c.getDrawable(tv.resourceId));
        return v;
    }

    public static TextView text(Context c, String s, float sp, boolean bold, int color) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp * FS); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return t;
    }

    public static Button button(Context c, String s, int color, View.OnClickListener l) {
        Button b = new Button(c); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(15 * FS); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setOnClickListener(l); b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{shade(color, 0.08), shade(color, -0.06)}); bg.setCornerRadius(dp(c, 8));
        android.graphics.drawable.RippleDrawable rp = new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), bg, null); b.setBackground(rp);
        int p = dp(c, 12); b.setPadding(p, p / 2, p, p / 2); b.setMinHeight(dp(c, 44)); b.setMinimumHeight(dp(c, 44));
        String ic = Icons.forText(s);
        if (ic != null && s.length() > 2) { b.setCompoundDrawablesRelative(Icons.get(ic, dp(c, 18), Color.WHITE), null, null, null); b.setCompoundDrawablePadding(dp(c, 6)); }
        return b;
    }

    public static EditText field(Context c, String hint, boolean number) {
        EditText e = new EditText(c); e.setHint(hint); e.setTextSize(16 * FS); e.setSingleLine(true); e.setTextColor(TEXT); e.setHintTextColor(MUTED);
        e.setBackground(outline(CARD, BORDER, 8, c)); int p = dp(c, 10); e.setPadding(p, p, p, p);
        e.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(outline(CARD, BORDER, 12, c)); l.setElevation(dp(c, 1));
        int p = dp(c, 12); l.setPadding(p, p, p, p); return l;
    }
    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    public static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    public static LinearLayout.LayoutParams gap(Context c, int topDp) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(c, topDp); return p; }
    public static void add(LinearLayout parent, View v, int topDp) { parent.addView(v, gap(parent.getContext(), topDp)); }

    /** سطر «عنوان: قيمة» */
    public static LinearLayout pair(Context c, String label, String value, int valueColor) {
        LinearLayout r = row(c);
        r.addView(text(c, label, 15, false, MUTED), weight(1));
        r.addView(text(c, value, 16, true, valueColor));
        return r;
    }

    // ------------------------------------------------ رسائل
    public static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }
    public static void alert(Context c, String msg) { new AlertDialog.Builder(c).setMessage(msg).setPositiveButton("موافق", null).show(); }
    public static void confirm(Context c, String msg, Runnable yes) {
        new AlertDialog.Builder(c).setMessage(msg).setPositiveButton("نعم", (d, w) -> yes.run()).setNegativeButton("لا", null).show();
    }
    public interface Text { void got(String s); }
    public static void input(Context c, String title, String hint, String value, boolean number, Text cb) {
        EditText e = field(c, hint, number); if (value != null) { e.setText(value); e.selectAll(); }
        LinearLayout box = new LinearLayout(c); int p = dp(c, 18); box.setPadding(p, dp(c, 8), p, 0); box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(c).setTitle(title).setView(box).setPositiveButton("موافق", (d, w) -> cb.got(e.getText().toString().trim()))
                .setNegativeButton("إلغاء", null).show();
        e.requestFocus();
    }
    public interface Pick { void picked(int index); }
    public static void choose(Context c, String title, List<String> items, Pick cb) {
        new AlertDialog.Builder(c).setTitle(title).setItems(items.toArray(new String[0]), (d, w) -> cb.picked(w)).show();
    }

    /** ينفّذ عملية محاسبية ويعرض رسالة الخطأ بدل انهيار التطبيق */
    public static boolean run(Context c, Runnable r) {
        try { r.run(); return true; }
        catch (Ops.OpException e) { alert(c, e.getMessage()); return false; }
        catch (RuntimeException e) { com.market.Store.logQuiet("android", e); alert(c, "حدث خطأ: " + e.getMessage()); return false; }
    }

    // ------------------------------------------------ قائمة بسيطة
    public interface Bind<T> { View view(T item, int pos); }
    public static final class Adapter<T> extends BaseAdapter {
        public final List<T> items = new ArrayList<>(); final Bind<T> bind;
        public Adapter(Bind<T> bind) { this.bind = bind; }
        public void set(List<T> l) { items.clear(); items.addAll(l); notifyDataSetChanged(); }
        public int getCount() { return items.size(); }
        public T getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View old, ViewGroup parent) { return bind.view(items.get(i), i); }
    }

    /** سطر جدول مثل جداول برنامج الكمبيوتر: صفوف متناوبة بخط فاصل */
    public static View listRow(Context c, String title, String sub, String value, int valueColor) {
        LinearLayout r = row(c); int p = dp(c, 12); r.setPadding(p, dp(c, 10), p, dp(c, 10));
        LinearLayout left = new LinearLayout(c); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(c, title, 15, true, TEXT));
        if (sub != null && !sub.isEmpty()) left.addView(text(c, sub, 12.5f, false, MUTED));
        r.addView(left, weight(1));
        if (value != null) { TextView v = text(c, value, 15, true, valueColor == 0 ? TEXT : valueColor); v.setPadding(dp(c, 8), 0, 0, 0); r.addView(v); }
        r.addView(new android.widget.ImageView(c) {{ setImageDrawable(Icons.get("chevron-left", dp(c, 16), MUTED)); setPadding(dp(c, 6), 0, 0, 0); }});
        android.graphics.drawable.LayerDrawable bg = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                new android.graphics.drawable.ColorDrawable(BORDER), new android.graphics.drawable.ColorDrawable(ROW)});
        bg.setLayerInset(1, 0, 0, 0, Math.max(1, dp(c, 1)));
        r.setBackground(bg);
        return r;
    }
    /** رأس جدول بلون رؤوس الجداول */
    public static View tableHead(Context c, String text) {
        TextView t = text(c, text, 13, true, TEXT); int p = dp(c, 10); t.setPadding(p, dp(c, 7), p, dp(c, 7)); t.setBackgroundColor(HEAD); return t;
    }
}
