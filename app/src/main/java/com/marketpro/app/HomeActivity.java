package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.market.Model;
import com.market.Model.InvType;
import com.market.Model.Product;
import com.market.Session;
import com.market.Store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** الشاشة الرئيسية: بلاطات ملونة مثل برنامج الحاسوب، وكل بلاطة تفتح قائمة عملياتها */
public class HomeActivity extends Activity {

    @Override protected void onResume() { super.onResume(); build(); }

    void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ux.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        // الشريط العلوي: اسم المحل والمستخدم
        LinearLayout top = Ux.row(this); int p = Ux.dp(this, 14); top.setPadding(p, p, p, p);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{Ux.PRIMARY, Ux.DARK}));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Ux.text(this, Store.db.get("storeName", "سوبر ماركت"), 20, true, Color.WHITE));
        names.addView(Ux.text(this, "MarketPro - " + Session.name(), 13, false, 0xCCFFFFFF));
        top.addView(names, Ux.weight(1));
        TextView out = Ux.text(this, "خروج", 14, true, Color.WHITE); out.setPadding(p, p / 2, p, p / 2); out.setBackground(Ux.round(0x33FFFFFF, 10, this));
        out.setOnClickListener(v -> { Session.user = null; startActivity(new Intent(this, LoginActivity.class)); finish(); });
        top.addView(out);
        root.addView(top);

        ScrollView sv = new ScrollView(this); LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int bp = Ux.dp(this, 10); body.setPadding(bp, bp, bp, bp);
        int low = 0; for (Product x : Store.db.products.values()) if (x.active && x.minQty > 0 && x.qty < x.minQty) low++;
        if (low > 0) { TextView t = Ux.text(this, low + " صنف تحت الحد الأدنى", 13, true, Color.WHITE); t.setPadding(p, p / 2, p, p / 2);
            t.setBackground(Ux.round(Ux.ORANGE, 14, this)); LinearLayout w = Ux.row(this); w.addView(t); body.addView(w); }

        List<Tile> tiles = new ArrayList<>();
        List<Menus.Section> secs = Menus.all();
        for (int k = 0; k < secs.size(); k++) { Menus.Section sc = secs.get(k); final int idx = k;
            if (sc.adminOnly && !com.market.Session.admin()) continue;
            boolean any = com.market.Session.admin(); for (Menus.Item it : sc.items) if (com.market.Session.can(it.title)) any = true;
            if (!any) continue;
            tiles.add(new Tile(sc.title, sc.icon, sc.color, () -> startActivity(new Intent(this, MenuActivity.class).putExtra("sec", idx)))); }
        tiles.add(0, new Tile("نقطة البيع", "🧾", 0xFF15803D, () -> inv(InvType.SALE)));
        LinearLayout line = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 2 == 0) { line = Ux.row(this); body.addView(line, Ux.gap(this, 8)); }
            View t = tiles.get(i).view(); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ux.dp(this, 108), 1);
            lp.setMarginStart(i % 2 == 0 ? 0 : Ux.dp(this, 8)); line.addView(t, lp);
        }
        if (tiles.size() % 2 == 1) line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        TextView foot = Ux.text(this, "الإصدار " + BuildConfig.VERSION_NAME + "  •  " + com.market.Session.name(), 12, false, Ux.MUTED); foot.setGravity(Gravity.CENTER);
        Ux.add(body, foot, 14);
        sv.addView(body); root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    final class Tile {
        final String title, icon; final int color; final Runnable action;
        Tile(String t, String i, int c, Runnable a) { title = t; icon = i; color = c; action = a; }
        View view() {
            LinearLayout v = new LinearLayout(HomeActivity.this); v.setOrientation(LinearLayout.VERTICAL); v.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{lighter(color), color}); g.setCornerRadius(Ux.dp(HomeActivity.this, 16));
            v.setBackground(g); v.setElevation(Ux.dp(HomeActivity.this, 2));
            TextView ic = Ux.text(HomeActivity.this, icon, 30, false, Color.WHITE); ic.setGravity(Gravity.CENTER); v.addView(ic);
            TextView t = Ux.text(HomeActivity.this, title, 16, true, Color.WHITE); t.setGravity(Gravity.CENTER); v.addView(t);
            v.setOnClickListener(x -> action.run());
            return v;
        }
    }
    static int lighter(int c) { int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | (Math.min(255, r + 40) << 16) | (Math.min(255, g + 40) << 8) | Math.min(255, b + 40); }

    void menu(String title, String[] items, Ux.Pick pick) { List<String> l = new ArrayList<>(Arrays.asList(items)); Ux.choose(this, title, l, pick); }
    void open(Class<?> c) { startActivity(new Intent(this, c)); }
    void inv(InvType t) { startActivity(new Intent(this, InvoiceActivity.class).putExtra("type", t.name())); }
    void log(InvType t) { startActivity(new Intent(this, InvoicesActivity.class).putExtra("type", t.name())); }
    void parties(int kind) { startActivity(new Intent(this, PartiesActivity.class).putExtra("kind", kind)); }
}
