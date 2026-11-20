package com.marketpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.market.Expiry;
import com.market.Model;
import com.market.Model.InvType;
import com.market.Model.Invoice;
import com.market.Model.Product;
import com.market.Model.Wallet;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDate;
import java.util.List;

/** الشاشة الرئيسية بنفس تصميم برنامج الكمبيوتر: الترويسة، التنبيهات، مؤشرات اليوم، وبطاقات الأقسام */
public class HomeActivity extends Activity {
    /** شكل الواجهة الحالي (فاتح أو داكن أو متدرج) */
    HomeStyle st = HomeStyle.current();
    @Override protected void onResume() { super.onResume(); build(); }

    void build() {
        st = HomeStyle.current();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackground(st.bg()); root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        androidx.drawerlayout.widget.DrawerLayout dl = Sidebar.wrap(this, root, "الرئيسية");
        // الترويسة: الشعار واسم المحل + المستخدم والخروج (مثل شريط الكمبيوتر)
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ux.dp(this, 10); top.setPadding(p, p, p, p);
        top.setBackground(st.head());
        top.addView(Ux.iconBtn(this, "menu", Color.WHITE, v -> dl.openDrawer(androidx.core.view.GravityCompat.START)));
        ImageView lg = new ImageView(this); lg.setImageDrawable(Sidebar.logo(this, Ux.dp(this, 38))); LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Ux.dp(this, 38), Ux.dp(this, 38)); ll.setMarginStart(Ux.dp(this, 4)); top.addView(lg, ll);
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(Ux.dp(this, 9), 0, 0, 0);
        TextView sn = Ux.text(this, Store.db.get("storeName", "سوبر ماركت"), 17, true, Color.WHITE); sn.setSingleLine(true); names.addView(sn);
        names.addView(Ux.text(this, "MarketPro - نظام محاسبة السوبر ماركت", 11, false, 0xFFC9D6EA));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(Ux.iconBtn(this, "sliders", Color.WHITE, v -> stylePicker()));
        top.addView(Ux.iconBtn(this, "palette", Color.WHITE, v -> themePicker()));
        top.addView(Ux.iconBtn(this, "logout", 0xFFFCA5A5, v -> { Session.user = null; startActivity(new Intent(this, LoginActivity.class)); finish(); }));
        root.addView(top);
        // الروابط السريعة
        LinearLayout quick = new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL); quick.setBackgroundColor(st.quick);
        quick.setPadding(Ux.dp(this, 4), 0, Ux.dp(this, 4), Ux.dp(this, 4));
        qlink(quick, "cart", "بيع", Menus.inv(InvType.SALE));
        if (Session.admin()) qlink(quick, "box", "شراء", Menus.inv(InvType.PURCHASE));
        qlink(quick, "tag", "سعر", Menus.open(ToolsActivity.class, "do", "price"));
        if (Session.admin()) qlink(quick, "chart", "التقارير", Menus.open(ReportsActivity.class));
        qlink(quick, "user", Session.name(), a -> Ux.alert(a, Session.name() + "\n" + (Session.user == null ? "" : Session.user.role)));
        root.addView(quick);

        ScrollView sv = new ScrollView(this); LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int bp = Ux.dp(this, 12); body.setPadding(bp, bp, bp, bp); sv.addView(body); root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        // التحية
        TextView hi = Ux.text(this, "أهلاً، " + Session.name(), 18, true, st.text); body.addView(hi);
        TextView storeSub = Ux.text(this, Store.db.get("storeName", "سوبر ماركت"), 12, false, st.muted); Ux.add(body, storeSub, 0);
        // التنبيهات
        long low = 0; for (Product x : Store.db.products.values()) if (x.active && x.minQty > 0 && x.qty <= x.minQty) low++;
        int ex = 0, soon = 0, alert = Expiry.alertDays(); try { for (Expiry.Row x : Expiry.open()) { if (x.days < 0) ex++; else if (x.days <= alert) soon++; } } catch (Exception ignored) {}
        LinearLayout chips = Ux.row(this); boolean anyChip = false;
        if (low > 0) { chip(chips, low + " صنف تحت الحد الأدنى", Ux.ORANGE, Menus.open(ToolsActivity.class, "do", "suggest")); anyChip = true; }
        if (ex > 0) { chip(chips, ex + " دفعة منتهية", Ux.RED, Menus.rep("المنتجات حسب تاريخ الانتهاء")); anyChip = true; }
        if (soon > 0) { chip(chips, soon + " قريبة الانتهاء", Ux.GOLD, Menus.rep("المنتجات حسب تاريخ الانتهاء")); anyChip = true; }
        if (anyChip) { android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this); hs.setHorizontalScrollBarEnabled(false); hs.addView(chips); body.addView(hs); }
        // مؤشرات اليوم
        if (Session.admin() || Session.cap("cap:cost")) stats(body);
        // بطاقات الأقسام
        GridLayout grid = new GridLayout(this); grid.setColumnCount(3); grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        List<Menus.Section> secs = Menus.all(); int w = (getResources().getDisplayMetrics().widthPixels - 2 * bp) / 3;
        for (int k = 0; k < secs.size(); k++) { Menus.Section sc = secs.get(k);
            if (sc.adminOnly && !Session.admin()) continue;
            boolean any = Session.admin(); for (Menus.Item it : sc.items) if (Session.can(it.title)) any = true; if (!any) continue;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = w - Ux.dp(this, 8); lp.height = Ux.dp(this, 112); lp.setMargins(Ux.dp(this, 4), Ux.dp(this, 4), Ux.dp(this, 4), Ux.dp(this, 4));
            grid.addView(tile(sc), lp); }
        Ux.add(body, grid, 10);
        TextView foot = Ux.text(this, "الإصدار " + BuildConfig.VERSION_NAME + "  •  " + Session.name(), 12, false, st.muted); foot.setGravity(Gravity.CENTER); Ux.add(body, foot, 12);
        setContentView(dl);
        getWindow().setStatusBarColor(st.headTop);
    }

    void qlink(LinearLayout bar, String icon, String label, Menus.Act act) {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER); int p = Ux.dp(this, 8); v.setPadding(p, p, p, p);
        ImageView i = new ImageView(this); i.setImageDrawable(Icons.get(icon, Ux.dp(this, 18), Color.WHITE)); v.addView(i);
        TextView t = Ux.text(this, label, 13, true, Color.WHITE); t.setPadding(Ux.dp(this, 5), 0, 0, 0); t.setSingleLine(true); v.addView(t);
        android.util.TypedValue tv = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true); v.setBackgroundResource(tv.resourceId);
        v.setOnClickListener(x -> Ux.run(this, () -> act.run(this)));
        bar.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
    }
    void chip(LinearLayout row, String text, int color, Menus.Act act) {
        TextView t = Ux.text(this, text, 13, true, Color.WHITE); int p = Ux.dp(this, 12); t.setPadding(p, Ux.dp(this, 6), p, Ux.dp(this, 6));
        t.setBackground(Ux.round(color, 14, this)); t.setOnClickListener(v -> Ux.run(this, () -> act.run(this)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMarginEnd(Ux.dp(this, 8)); row.addView(t, lp);
    }
    void stats(LinearLayout body) {
        LocalDate today = LocalDate.now(); double sales = 0, profit = 0, purch = 0; int count = 0;
        for (Invoice i : Store.db.invoices) if (!i.cancelled && i.date.toLocalDate().equals(today)) {
            if (i.type == InvType.SALE) { sales += i.total; profit += i.total - i.tax - i.costTotal(); count++; }
            else if (i.type == InvType.SALE_RETURN) { sales -= i.total; profit -= i.total - i.tax - i.costTotal(); }
            else if (i.type == InvType.PURCHASE) purch += i.total; }
        double cash = 0; for (Wallet w : Store.db.activeWallets()) cash += Ops.walletBalance(w.id);
        double rec = 0; for (double v : Ops.allBalances(Model.CUSTOMER).values()) rec += v;
        GridLayout g = new GridLayout(this); g.setColumnCount(2); g.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int w = (getResources().getDisplayMetrics().widthPixels - Ux.dp(this, 24)) / 2;
        stat(g, w, "مبيعات اليوم", Ui.m(sales), count + " فاتورة", Ux.GREEN, "cart");
        stat(g, w, "ربح اليوم", Ui.m(profit), "تقديري", Ux.PRIMARY, "chart");
        stat(g, w, "النقدية", Ui.m(cash), "كل المحافظ", Ux.GOLD, "wallet");
        stat(g, w, "ذمم العملاء", Ui.m(rec), "مشتريات اليوم " + Ui.m(purch), Ux.PURPLE, "users");
        Ux.add(body, g, 8);
    }
    void stat(GridLayout g, int w, String t, String v, String sub, int c, String icon) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(st.card(this)); card.setElevation(st.dark ? 0 : Ux.dp(this, 1)); int p = Ux.dp(this, 10); card.setPadding(p, p, p, p);
        GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor((c & 0x00FFFFFF) | (Ux.DARK_MODE ? 0x3C000000 : 0x1E000000));
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{circle, Icons.get(icon, Ux.dp(this, 20), c)});
        int in = Ux.dp(this, 9); ld.setLayerInset(1, in, in, in, in);
        ImageView ic = new ImageView(this); ic.setImageDrawable(ld); card.addView(ic, new LinearLayout.LayoutParams(Ux.dp(this, 38), Ux.dp(this, 38)));
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL); txt.setPadding(Ux.dp(this, 8), 0, 0, 0);
        txt.addView(Ux.text(this, t, 12, false, st.muted)); TextView vv = Ux.text(this, v, 17, true, c); vv.setSingleLine(true); txt.addView(vv); txt.addView(Ux.text(this, sub, 10.5f, false, st.muted));
        card.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = w - Ux.dp(this, 8); lp.setMargins(Ux.dp(this, 4), Ux.dp(this, 4), Ux.dp(this, 4), Ux.dp(this, 4));
        g.addView(card, lp);
    }

    /** بطاقة قسم بتدرّج لونه وأيقونته — الضغط يفتح قائمة عملياته (مثل الكمبيوتر) */
    View tile(Menus.Section s) {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ux.shade(s.color, 0.12), Ux.shade(s.color, -0.1)}); g.setCornerRadius(Ux.dp(this, 16));
        v.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), g, null)); v.setElevation(Ux.dp(this, 2));
        ImageView ic = new ImageView(this); ic.setImageDrawable(Icons.get(s.vicon, Ux.dp(this, 40), Color.WHITE)); v.addView(ic);
        TextView t = Ux.text(this, s.title, 13, true, Color.WHITE); t.setGravity(Gravity.CENTER); t.setPadding(Ux.dp(this, 4), Ux.dp(this, 6), Ux.dp(this, 4), 0); t.setMaxLines(2); v.addView(t);
        v.setOnClickListener(x -> tileMenu(s));
        return v;
    }
    /** قائمة القسم: ترويسة ملوّنة بلون القسم + صفوف بسهم */
    void tileMenu(Menus.Section s) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); box.setBackgroundColor(Ux.CARD);
        LinearLayout head = new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL); int p = Ux.dp(this, 12); head.setPadding(p, p, p, p);
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{Ux.shade(s.color, 0.14), Ux.shade(s.color, -0.12)}));
        ImageView ic = new ImageView(this); ic.setImageDrawable(Icons.get(s.vicon, Ux.dp(this, 22), Color.WHITE)); head.addView(ic);
        TextView ht = Ux.text(this, s.title, 16, true, Color.WHITE); ht.setPadding(Ux.dp(this, 9), 0, 0, 0); head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        int n = 0; for (Menus.Item it : s.items) if (Session.admin() || Session.can(it.title)) n++;
        head.addView(Ux.text(this, n + " عمليات", 11, false, 0xDCFFFFFF));
        box.addView(head);
        ScrollView sv = new ScrollView(this); LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); sv.addView(rows); box.addView(sv);
        AlertDialog d = new Dlg(this).setView(box).create();
        for (Menus.Item it : s.items) {
            if (!Session.admin() && !Session.can(it.title)) continue;
            LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(Ux.dp(this, 14), Ux.dp(this, 11), Ux.dp(this, 12), Ux.dp(this, 11));
            r.addView(Ux.text(this, it.title, 15, false, Ux.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
            ImageView chv = new ImageView(this); chv.setImageDrawable(Icons.get("chevron-left", Ux.dp(this, 14), Ux.MUTED)); r.addView(chv);
            android.util.TypedValue tv = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true); r.setBackgroundResource(tv.resourceId);
            r.setOnClickListener(x -> { d.dismiss(); Ux.run(this, () -> it.act.run(this)); });
            rows.addView(r);
            View sep = new View(this); sep.setBackgroundColor(Ux.BORDER); rows.addView(sep, new LinearLayout.LayoutParams(-1, 1));
        }
        d.show();
    }

    /** نافذة اختيار الثيم: كل ثيم بطاقة بألوانه (نفس ثيمات الكمبيوتر) */
    void themePicker() { SettingsActivity.pickTheme(this, this::build); }

    /** نافذة اختيار شكل الواجهة الرئيسية: 8 معاينات، والتطبيق فوري بإعادة بناء الشاشة */
    void stylePicker() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); box.setBackgroundColor(Ux.CARD);
        int p = Ux.dp(this, 14); box.setPadding(p, p, p, p);
        box.addView(Ux.text(this, "شكل الواجهة الرئيسية", 17, true, Ux.TEXT));
        box.addView(Ux.text(this, "يتغير الشكل فوراً عند الاختيار", 12, false, Ux.MUTED));
        GridLayout g = new GridLayout(this); g.setColumnCount(2); g.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int cur = HomeStyle.index();
        AlertDialog d = new Dlg(this).setView(box).create();
        int w = (getResources().getDisplayMetrics().widthPixels - Ux.dp(this, 80)) / 2;
        for (int i = 0; i < HomeStyle.ALL.length; i++) {
            final int k = i;
            View cell = preview(HomeStyle.ALL[i], i == cur);
            cell.setOnClickListener(v -> { HomeStyle.select(k); d.dismiss(); build(); });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = w;
            lp.setMargins(Ux.dp(this, 4), Ux.dp(this, 6), Ux.dp(this, 4), Ux.dp(this, 6));
            g.addView(cell, lp);
        }
        box.addView(g);
        d.show();
    }

    /** معاينة مصغّرة لشكل: خلفية بتدرّجه وترويسة وبطاقتان، والمحدد عليه إطار بلون التمييز */
    View preview(HomeStyle s, boolean selected) {
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL); col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout fr = new FrameLayout(this);
        GradientDrawable bg = s.bg(); bg.setCornerRadius(Ux.dp(this, 14));
        bg.setStroke(Ux.dp(this, selected ? 3 : 1), selected ? Ux.PRIMARY : s.stroke);
        fr.setBackground(bg);
        View hd = new View(this); hd.setBackground(s.head());
        fr.addView(hd, new FrameLayout.LayoutParams(-1, Ux.dp(this, 22)));
        View c1 = new View(this); c1.setBackground(s.card(this));
        FrameLayout.LayoutParams l1 = new FrameLayout.LayoutParams(Ux.dp(this, 50), Ux.dp(this, 30), Gravity.LEFT | Gravity.TOP);
        l1.setMargins(Ux.dp(this, 8), Ux.dp(this, 32), 0, 0); fr.addView(c1, l1);
        View c2 = new View(this); c2.setBackground(s.card(this));
        FrameLayout.LayoutParams l2 = new FrameLayout.LayoutParams(Ux.dp(this, 50), Ux.dp(this, 30), Gravity.RIGHT | Gravity.TOP);
        l2.setMargins(0, Ux.dp(this, 32), Ux.dp(this, 8), 0); fr.addView(c2, l2);
        View c3 = new View(this); c3.setBackground(s.card(this));
        FrameLayout.LayoutParams l3 = new FrameLayout.LayoutParams(-1, Ux.dp(this, 16), Gravity.TOP);
        l3.setMargins(Ux.dp(this, 8), Ux.dp(this, 68), Ux.dp(this, 8), 0); fr.addView(c3, l3);
        col.addView(fr, new LinearLayout.LayoutParams(-1, Ux.dp(this, 96)));
        TextView name = Ux.text(this, s.name, 13, selected, Ux.TEXT); name.setGravity(Gravity.CENTER); name.setPadding(0, Ux.dp(this, 6), 0, 0);
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }
}
