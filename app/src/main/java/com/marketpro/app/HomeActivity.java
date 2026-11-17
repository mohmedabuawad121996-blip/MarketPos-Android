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

        List<Tile> tiles = Arrays.asList(
                new Tile("المبيعات", "🛒", 0xFF16A34A, () -> menu("المبيعات", new String[]{"نقطة البيع (فاتورة مبيعات)", "مرتجع مبيعات", "سجل فواتير المبيعات", "سجل مرتجعات المبيعات"},
                        i -> { if (i == 0) inv(InvType.SALE); else if (i == 1) inv(InvType.SALE_RETURN); else if (i == 2) log(InvType.SALE); else log(InvType.SALE_RETURN); })),
                new Tile("المشتريات", "📦", 0xFF047857, () -> menu("المشتريات", new String[]{"فاتورة مشتريات", "مرتجع مشتريات", "تسجيل طلبية (لا تدخل المخزون)", "سجل فواتير المشتريات", "سجل الطلبيات"},
                        i -> { if (i == 0) inv(InvType.PURCHASE); else if (i == 1) inv(InvType.PURCHASE_RETURN); else if (i == 2) inv(InvType.ORDER); else if (i == 3) log(InvType.PURCHASE); else log(InvType.ORDER); })),
                new Tile("المخزون", "🏷", 0xFF0D9488, () -> open(ProductsActivity.class)),
                new Tile("العملاء", "👥", 0xFF7C3AED, () -> parties(Model.CUSTOMER)),
                new Tile("الموردين", "🚚", 0xFFEA580C, () -> parties(Model.SUPPLIER)),
                new Tile("المصروفات", "💸", 0xFFDC2626, () -> open(ExpensesActivity.class)),
                new Tile("الصندوق", "👛", 0xFFA16207, () -> open(CashActivity.class)),
                new Tile("التقارير", "📊", 0xFF2563EB, () -> open(ReportsActivity.class)),
                new Tile("الإعدادات", "⚙", 0xFF475569, () -> open(SettingsActivity.class)),
                new Tile("النسخ الاحتياطي", "💾", 0xFF334155, () -> open(BackupActivity.class)));
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
