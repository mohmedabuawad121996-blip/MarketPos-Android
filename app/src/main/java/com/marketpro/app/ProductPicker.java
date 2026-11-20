package com.marketpro.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.market.Model.Category;
import com.market.Model.Product;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * نافذة إضافة الأصناف من شاشة البيع: تصفّح بالتصنيفات وبحث فوري وبطاقات عصرية.
 * الضغط على البطاقة يضيف الصنف والنافذة تبقى مفتوحة لإضافة أصناف أخرى.
 */
public final class ProductPicker {
    public interface OnPick { void pick(Product p); }

    private static final int MAX_SHOWN = 120;

    public static void show(Context c, boolean buy, OnPick onPick) {
        List<Product> all = new ArrayList<>();
        for (Product p : Store.db.products.values()) if (p.active) all.add(p);
        all.sort((a, b) -> (a.name == null ? "" : a.name).compareToIgnoreCase(b.name == null ? "" : b.name));

        Ux.Win w = new Ux.Win(c, "إضافة أصناف", "box");
        EditText search = Ux.modernField(Ux.field(c, "ابحث بالاسم أو الباركود", false));
        w.add(search);

        final long[] cat = {0};
        HorizontalScrollView hs = new HorizontalScrollView(c); hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(c); chips.setOrientation(LinearLayout.HORIZONTAL); chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        hs.addView(chips); w.add(hs);

        TextView count = Ux.text(c, "", 12, false, Ux.MUTED); count.setPadding(0, Ux.dp(c, 8), 0, Ux.dp(c, 4));
        w.add(count);

        GridLayout grid = new GridLayout(c); grid.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        w.add(grid);

        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int cols = Math.max(2, screenW / Ux.dp(c, 160));
        int cellW = (screenW - Ux.dp(c, 80)) / cols;

        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            chips.removeAllViews();
            addChip(c, chips, "الكل", cat[0] == 0, () -> { cat[0] = 0; fill[0].run(); });
            for (Category k : Store.db.categories) {
                final long id = k.id;
                addChip(c, chips, k.name, cat[0] == id, () -> { cat[0] = id; fill[0].run(); });
            }
            grid.removeAllViews(); grid.setColumnCount(cols);
            String q = search.getText().toString().trim().toLowerCase();
            int shown = 0, total = 0;
            for (Product p : all) {
                if (cat[0] != 0 && p.categoryId != cat[0]) continue;
                if (!q.isEmpty()) {
                    boolean hit = (p.name != null && p.name.toLowerCase().contains(q)) || (p.barcode != null && p.barcode.startsWith(q));
                    if (!hit) continue;
                }
                total++;
                if (shown >= MAX_SHOWN) continue;
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cellW; lp.setMargins(Ux.dp(c, 4), Ux.dp(c, 4), Ux.dp(c, 4), Ux.dp(c, 4));
                grid.addView(card(c, p, buy, onPick), lp);
                shown++;
            }
            count.setText(total == 0 ? "لا توجد أصناف مطابقة"
                    : "عدد الأصناف: " + total + (total > shown ? "  —  ضيّق البحث لتظهر الباقي" : ""));
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c2) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c2) { fill[0].run(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        fill[0].run();
        w.show();
        if (w.d.getWindow() != null) w.d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** شريحة تصنيف: المختارة بلون التمييز وغير المختارة بإطار هادئ */
    static void addChip(Context c, LinearLayout row, String label, boolean selected, Runnable onClick) {
        TextView t = Ux.text(c, label, 13, true, selected ? Color.WHITE : Ux.TEXT);
        int ph = Ux.dp(c, 14);
        t.setPadding(ph, Ux.dp(c, 7), ph, Ux.dp(c, 7));
        t.setBackground(Ux.outline(selected ? Ux.PRIMARY : Ux.CARD, selected ? Ux.PRIMARY : Ux.BORDER, 18, c));
        t.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(Ux.dp(c, 8)); lp.topMargin = Ux.dp(c, 4); lp.bottomMargin = Ux.dp(c, 4);
        row.addView(t, lp);
    }

    /** بطاقة صنف: الاسم، المخزون (أحمر عند الحد الأدنى)، السعر، وزر + */
    static View card(Context c, Product p, boolean buy, OnPick onPick) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL);
        int pd = Ux.dp(c, 10); v.setPadding(pd, pd, pd, pd);
        v.setBackground(Ux.outline(Ux.CARD, Ux.BORDER, 14, c));
        v.setElevation(Ux.dp(c, 1));

        TextView name = Ux.text(c, p.name == null ? "" : p.name, 14, true, Ux.TEXT);
        name.setMaxLines(2); name.setMinLines(2);
        v.addView(name);

        boolean low = p.minQty > 0 && p.qty <= p.minQty;
        TextView stock = Ux.text(c, p.stockText(), 11, false, low ? Ux.RED : Ux.MUTED);
        stock.setPadding(0, Ux.dp(c, 4), 0, 0);
        v.addView(stock);

        LinearLayout bottom = new LinearLayout(c); bottom.setOrientation(LinearLayout.HORIZONTAL); bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, Ux.dp(c, 8), 0, 0);
        TextView price = Ux.text(c, Ui.m(buy ? p.cost : p.price), 16, true, Ux.PRIMARY);
        bottom.addView(price, new LinearLayout.LayoutParams(0, -2, 1));

        TextView plus = Ux.text(c, "+", 20, true, Color.WHITE); plus.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(Ux.GREEN);
        plus.setBackground(circle);
        bottom.addView(plus, new LinearLayout.LayoutParams(Ux.dp(c, 32), Ux.dp(c, 32)));
        v.addView(bottom);

        v.setOnClickListener(x -> {
            onPick.pick(p);
            Toast.makeText(c, "أُضيف: " + p.name, Toast.LENGTH_SHORT).show();
        });
        return v;
    }
}
