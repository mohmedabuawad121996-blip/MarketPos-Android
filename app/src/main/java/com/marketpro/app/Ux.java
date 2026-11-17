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

/** أدوات بناء الواجهة برمجياً بنفس ألوان برنامج الحاسوب (ثيم أخضر طبيعي) */
public final class Ux {
    private Ux() {}

    public static final int PRIMARY = 0xFF047857, DARK = 0xFF065F46, TEAL = 0xFF0F766E, BLUE = 0xFF2563EB, RED = 0xFFDC2626,
            ORANGE = 0xFFEA580C, PURPLE = 0xFF7C3AED, GREEN = 0xFF16A34A, BG = 0xFFF1F5F4, CARD = 0xFFFFFFFF, TEXT = 0xFF0F172A,
            MUTED = 0xFF64748B, BORDER = 0xFFE2E8F0;

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, radiusDp)); return g;
    }
    public static GradientDrawable outline(int fill, int stroke, float radiusDp, Context c) {
        GradientDrawable g = round(fill, radiusDp, c); g.setStroke(dp(c, 1), stroke); return g;
    }

    /** هيكل الشاشة: شريط علوي (عنوان + رجوع) ثم محتوى. يرجع الحاوية التي تُضاف إليها العناصر */
    public static LinearLayout screen(Activity a, String title, boolean scroll) {
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(header(a, title, true));
        LinearLayout body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 12); body.setPadding(p, p, p, p);
        if (scroll) {
            ScrollView sv = new ScrollView(a); sv.setFillViewport(true); sv.addView(body);
            root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        } else root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        a.setContentView(root);
        return body;
    }

    public static View header(Activity a, String title, boolean back) {
        LinearLayout h = new LinearLayout(a); h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{PRIMARY, DARK}); h.setBackground(g);
        int p = dp(a, 12); h.setPadding(p, p, p, p);
        if (back) {
            TextView b = text(a, "→", 22, true, Color.WHITE); b.setPadding(dp(a, 4), 0, dp(a, 14), 0);
            b.setOnClickListener(v -> a.finish()); h.addView(b);
        }
        TextView t = text(a, title, 19, true, Color.WHITE); h.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return h;
    }

    public static TextView text(Context c, String s, float sp, boolean bold, int color) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return t;
    }

    public static Button button(Context c, String s, int color, View.OnClickListener l) {
        Button b = new Button(c); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(15); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setBackground(round(color, 10, c)); b.setOnClickListener(l);
        int p = dp(c, 10); b.setPadding(p, p / 2, p, p / 2); b.setMinHeight(dp(c, 46));
        return b;
    }

    public static EditText field(Context c, String hint, boolean number) {
        EditText e = new EditText(c); e.setHint(hint); e.setTextSize(16); e.setSingleLine(true);
        e.setBackground(outline(Color.WHITE, BORDER, 10, c)); int p = dp(c, 10); e.setPadding(p, p, p, p);
        e.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(outline(CARD, BORDER, 14, c));
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

    /** بطاقة سطر في قائمة: عنوان كبير، ونص فرعي، وقيمة على الطرف */
    public static View listRow(Context c, String title, String sub, String value, int valueColor) {
        LinearLayout r = row(c); int p = dp(c, 12); r.setPadding(p, p, p, p);
        r.setBackground(outline(CARD, BORDER, 12, c));
        LinearLayout left = new LinearLayout(c); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(c, title, 16, true, TEXT));
        if (sub != null && !sub.isEmpty()) left.addView(text(c, sub, 13, false, MUTED));
        r.addView(left, weight(1));
        if (value != null) r.addView(text(c, value, 16, true, valueColor));
        LinearLayout wrap = new LinearLayout(c); wrap.setPadding(0, dp(c, 3), 0, dp(c, 3)); wrap.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }
}
