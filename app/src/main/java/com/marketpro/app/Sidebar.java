package com.marketpro.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.market.Model.InvType;
import com.market.Session;
import com.market.Store;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** اللائحة الجانبية: نفس أقسام وشكل القائمة الجانبية في برنامج الكمبيوتر */
public final class Sidebar {
    private Sidebar() {}
    static final Set<String> open = new HashSet<>();

    /** يلفّ محتوى الشاشة بلائحة جانبية تُفتح بزر ☰ أو بالسحب من اليمين */
    public static DrawerLayout wrap(Activity a, View content, String current) {
        DrawerLayout dl = new DrawerLayout(a); dl.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        dl.addView(content, new DrawerLayout.LayoutParams(-1, -1));
        View side = build(a, dl, current);
        DrawerLayout.LayoutParams lp = new DrawerLayout.LayoutParams(Math.min(Ux.dp(a, 300), (int) (a.getResources().getDisplayMetrics().widthPixels * 0.84)), -1);
        lp.gravity = GravityCompat.START; dl.addView(side, lp);
        dl.setScrimColor(0x66000000);
        return dl;
    }

    /** شعار البرنامج: مربع بتدرّج أزرق-أخضر وعربة تسوق (نفس أيقونة نافذة الكمبيوتر) */
    public static Drawable logo(Context c, int size) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2E7BE6, 0xFF16B38A}); g.setCornerRadius(size * 0.21f);
        int pad = (int) (size * 0.18);
        LayerDrawable l = new LayerDrawable(new Drawable[]{g, Icons.get("cart", size - 2 * pad, Color.WHITE)});
        l.setLayerInset(1, pad, pad + size / 40, pad, pad - size / 40); l.setBounds(0, 0, size, size);
        return l;
    }
    /** أيقونة القسم داخل مربع ملوّن بحواف دائرية */
    static Drawable secIcon(Context c, Menus.Section s, int size) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ux.shade(s.color, 0.15), Ux.shade(s.color, -0.1)}); g.setCornerRadius(size * 0.25f);
        int in = (int) (size * 0.22);
        LayerDrawable l = new LayerDrawable(new Drawable[]{g, Icons.get(s.vicon, size - 2 * in, Color.WHITE)}); l.setLayerInset(1, in, in, in, in);
        return l;
    }

    static View build(Activity a, DrawerLayout dl, String current) {
        LinearLayout side = new LinearLayout(a); side.setOrientation(LinearLayout.VERTICAL); side.setBackgroundColor(Ux.SIDEBAR);
        side.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); side.setClickable(true);
        // الترويسة: الشعار واسم المحل
        LinearLayout top = new LinearLayout(a); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ux.dp(a, 12); top.setPadding(p, Ux.dp(a, 16), p, p);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{Ux.HEADER, Ux.shade(Ux.HEADER, Ux.DARK_MODE ? 0.08 : 0.18)}));
        ImageView lg = new ImageView(a); lg.setImageDrawable(logo(a, Ux.dp(a, 40))); top.addView(lg, new LinearLayout.LayoutParams(Ux.dp(a, 40), Ux.dp(a, 40)));
        LinearLayout names = new LinearLayout(a); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(Ux.dp(a, 10), 0, 0, 0);
        names.addView(Ux.text(a, Store.db.get("storeName", "سوبر ماركت"), 16, true, Color.WHITE));
        names.addView(Ux.text(a, "MarketPro - نظام محاسبة السوبر ماركت", 11, false, 0xFFC9D6EA));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        side.addView(top);
        // المستخدم والخروج
        LinearLayout who = new LinearLayout(a); who.setOrientation(LinearLayout.HORIZONTAL); who.setGravity(Gravity.CENTER_VERTICAL); who.setPadding(p, Ux.dp(a, 8), p, Ux.dp(a, 8));
        ImageView av = new ImageView(a); GradientDrawable avBg = new GradientDrawable(); avBg.setShape(GradientDrawable.OVAL); avBg.setColor(Ux.SIDE_ITEM);
        LayerDrawable avL = new LayerDrawable(new Drawable[]{avBg, Icons.get("user", Ux.dp(a, 20), Ux.SIDE_TEXT)}); avL.setLayerInset(1, Ux.dp(a, 8), Ux.dp(a, 8), Ux.dp(a, 8), Ux.dp(a, 8));
        av.setImageDrawable(avL); who.addView(av, new LinearLayout.LayoutParams(Ux.dp(a, 36), Ux.dp(a, 36)));
        LinearLayout wn = new LinearLayout(a); wn.setOrientation(LinearLayout.VERTICAL); wn.setPadding(Ux.dp(a, 9), 0, 0, 0);
        wn.addView(Ux.text(a, Session.name(), 14, true, Color.WHITE)); wn.addView(Ux.text(a, Session.user == null ? "" : Session.user.role, 11, false, 0xFFC9D6EA));
        who.addView(wn, new LinearLayout.LayoutParams(0, -2, 1));
        who.addView(Ux.iconBtn(a, "logout", 0xFFFCA5A5, v -> { Session.user = null; a.startActivity(new Intent(a, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK)); a.finish(); }));
        side.addView(who);
        // روابط سريعة مثل شريط الكمبيوتر العلوي
        LinearLayout quick = new LinearLayout(a); quick.setOrientation(LinearLayout.HORIZONTAL); quick.setPadding(Ux.dp(a, 6), 0, Ux.dp(a, 6), Ux.dp(a, 6));
        quick.addView(quick(a, dl, "home", "الرئيسية", x -> x.startActivity(new Intent(x, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP))), new LinearLayout.LayoutParams(0, -2, 1));
        quick.addView(quick(a, dl, "cart", "بيع", Menus.inv(InvType.SALE)), new LinearLayout.LayoutParams(0, -2, 1));
        if (Session.admin()) quick.addView(quick(a, dl, "box", "شراء", Menus.inv(InvType.PURCHASE)), new LinearLayout.LayoutParams(0, -2, 1));
        quick.addView(quick(a, dl, "tag", "سعر", Menus.open(ToolsActivity.class, "do", "price")), new LinearLayout.LayoutParams(0, -2, 1));
        if (Session.admin()) quick.addView(quick(a, dl, "chart", "التقارير", Menus.open(ReportsActivity.class)), new LinearLayout.LayoutParams(0, -2, 1));
        side.addView(quick);
        View sep = new View(a); sep.setBackgroundColor(0x19FFFFFF); side.addView(sep, new LinearLayout.LayoutParams(-1, 1));

        ScrollView sv = new ScrollView(a); LinearLayout list = new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(Ux.dp(a, 8), Ux.dp(a, 6), Ux.dp(a, 8), Ux.dp(a, 8));
        sv.addView(list); side.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        List<Menus.Section> secs = Menus.all();
        for (Menus.Section s : secs) {
            if (s.adminOnly && !Session.admin()) continue;
            boolean any = Session.admin(); for (Menus.Item it : s.items) if (Session.can(it.title)) any = true; if (!any) continue;
            boolean has = false; for (Menus.Item it : s.items) if (it.title.equals(current)) has = true;
            if (has) open.add(s.title);
            LinearLayout items = new LinearLayout(a); items.setOrientation(LinearLayout.VERTICAL); items.setPadding(0, Ux.dp(a, 2), 0, Ux.dp(a, 6));
            items.setVisibility(open.contains(s.title) ? View.VISIBLE : View.GONE);
            // رأس القسم
            LinearLayout head = new LinearLayout(a); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Ux.dp(a, 8), Ux.dp(a, 7), Ux.dp(a, 8), Ux.dp(a, 7));
            ImageView ic = new ImageView(a); ic.setImageDrawable(secIcon(a, s, Ux.dp(a, 30))); head.addView(ic, new LinearLayout.LayoutParams(Ux.dp(a, 30), Ux.dp(a, 30)));
            TextView ht = Ux.text(a, s.title, 14.5f, true, has ? Color.WHITE : Ux.SIDE_TEXT); ht.setPadding(Ux.dp(a, 10), 0, 0, 0); head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView ch = new ImageView(a); ch.setImageDrawable(Icons.get(items.getVisibility() == View.VISIBLE ? "chevron-down" : "chevron-left", Ux.dp(a, 16), Ux.SIDE_TEXT)); head.addView(ch);
            head.setBackground(items.getVisibility() == View.VISIBLE ? Ux.round(Ux.SIDE_ITEM, 8, a) : null);
            head.setOnClickListener(v -> { boolean o = items.getVisibility() != View.VISIBLE; items.setVisibility(o ? View.VISIBLE : View.GONE);
                if (o) open.add(s.title); else open.remove(s.title);
                ch.setImageDrawable(Icons.get(o ? "chevron-down" : "chevron-left", Ux.dp(a, 16), Ux.SIDE_TEXT)); head.setBackground(o ? Ux.round(Ux.SIDE_ITEM, 8, a) : null); });
            for (Menus.Item it : s.items) {
                if (!Session.admin() && !Session.can(it.title)) continue;
                boolean act = it.title.equals(current);
                TextView b = Ux.text(a, it.title, 14, act, act ? Color.WHITE : Ux.shade(Ux.SIDE_TEXT, -0.12));
                b.setPadding(Ux.dp(a, 48), Ux.dp(a, 8), Ux.dp(a, 8), Ux.dp(a, 8));
                if (act) { GradientDrawable g = new GradientDrawable(); g.setColor(0x1EFFFFFF); g.setCornerRadius(Ux.dp(a, 6)); g.setStroke(0, 0); b.setBackground(g); }
                else { android.util.TypedValue tv = new android.util.TypedValue(); a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true); b.setBackgroundResource(tv.resourceId); }
                b.setOnClickListener(v -> go(a, dl, it));
                items.addView(b);
            }
            list.addView(head); list.addView(items);
            View g = new View(a); list.addView(g, new LinearLayout.LayoutParams(1, Ux.dp(a, 3)));
        }
        // «حول البرنامج» ثابت أسفل اللائحة
        View line = new View(a); line.setBackgroundColor(0x19FFFFFF); side.addView(line, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout about = new LinearLayout(a); about.setOrientation(LinearLayout.HORIZONTAL); about.setGravity(Gravity.CENTER_VERTICAL);
        about.setPadding(Ux.dp(a, 16), Ux.dp(a, 10), Ux.dp(a, 16), Ux.dp(a, 12));
        Menus.Section info = new Menus.Section("حول البرنامج", "", 0xFF64748B, false); info.vicon = "info";
        ImageView ai = new ImageView(a); ai.setImageDrawable(secIcon(a, info, Ux.dp(a, 30))); about.addView(ai, new LinearLayout.LayoutParams(Ux.dp(a, 30), Ux.dp(a, 30)));
        TextView at = Ux.text(a, "حول البرنامج", 14.5f, true, Ux.SIDE_TEXT); at.setPadding(Ux.dp(a, 10), 0, 0, 0); about.addView(at);
        about.setOnClickListener(v -> { dl.closeDrawer(GravityCompat.START); a.startActivity(new Intent(a, SettingsActivity.class).putExtra("sec", "about")); });
        side.addView(about);
        return side;
    }

    static View quick(Activity a, DrawerLayout dl, String icon, String label, Menus.Act act) {
        LinearLayout v = new LinearLayout(a); v.setOrientation(LinearLayout.VERTICAL); v.setGravity(Gravity.CENTER); v.setPadding(0, Ux.dp(a, 6), 0, Ux.dp(a, 6));
        ImageView i = new ImageView(a); i.setImageDrawable(Icons.get(icon, Ux.dp(a, 22), Color.WHITE)); v.addView(i);
        v.addView(Ux.text(a, label, 11, false, Ux.SIDE_TEXT));
        android.util.TypedValue tv = new android.util.TypedValue(); a.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true); v.setBackgroundResource(tv.resourceId);
        v.setOnClickListener(x -> { dl.closeDrawer(GravityCompat.START); Ux.run(a, () -> act.run(a)); });
        return v;
    }

    /** فتح شاشة من اللائحة: تُغلق الشاشة الحالية (ما عدا الرئيسية والفاتورة) حتى لا تتراكم الشاشات */
    static void go(Activity a, DrawerLayout dl, Menus.Item it) {
        dl.closeDrawer(GravityCompat.START);
        if (!Ux.run(a, () -> it.act.run(a))) return;
        if (it.opens && !(a instanceof HomeActivity) && !(a instanceof InvoiceActivity)) a.finish();
    }
}
