package com.marketpro.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.market.Store;

/** عرض التقرير كجدول قابل للتمرير مع مشاركة CSV أو نص */
public class ReportViewActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ReportsActivity.Report r = ReportsActivity.current;
        if (r == null) { finish(); return; }
        LinearLayout body = Ux.screen(this, r.title, false);
        String store = Store.db.settings.getOrDefault("storeName", "");
        body.addView(Ux.text(this, (store.isEmpty() ? "" : store + "  •  ") + r.sub, 13, false, Ux.MUTED));
        LinearLayout btns = Ux.row(this);
        btns.addView(Ux.button(this, "مشاركة Excel (CSV)", Ux.BLUE, v -> Share.csv(this, r.title, r.head, withTotal(r))), Ux.weight(1));
        View g = new View(this); btns.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        btns.addView(Ux.button(this, "مشاركة نص", Ux.TEAL, v -> Share.text(this, r.title, asText(r))), Ux.weight(1));
        Ux.add(body, btns, 8);
        if (r.rows.isEmpty()) { Ux.add(body, Ux.text(this, "لا توجد بيانات في هذه الفترة", 15, true, Ux.MUTED), 20); return; }
        TableLayout t = new TableLayout(this); t.setStretchAllColumns(false); t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        t.addView(tr(r.head, Ux.PRIMARY, 0xFFFFFFFF, true));
        int i = 0; for (String[] row : r.rows) t.addView(tr(row, i++ % 2 == 0 ? Ux.CARD : 0xFFF8FAFC, Ux.TEXT, false));
        if (r.total != null) t.addView(tr(r.total, 0xFFD1FAE5, Ux.DARK, true));
        HorizontalScrollView h = new HorizontalScrollView(this); h.addView(t);
        ScrollView v = new ScrollView(this); v.addView(h);
        body.addView(v, new LinearLayout.LayoutParams(-1, 0, 1));
        ((LinearLayout.LayoutParams) v.getLayoutParams()).topMargin = Ux.dp(this, 8);
    }
    TableRow tr(String[] cells, int bg, int fg, boolean bold) {
        TableRow row = new TableRow(this); row.setBackgroundColor(bg);
        for (int c = 0; c < cells.length; c++) {
            TextView tv = new TextView(this); tv.setText(cells[c] == null ? "" : cells[c]); tv.setTextColor(fg); tv.setTextSize(14);
            if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
            int p = Ux.dp(this, 8); tv.setPadding(p, Ux.dp(this, 7), p, Ux.dp(this, 7));
            tv.setGravity(c == 0 ? Gravity.START : Gravity.CENTER); if (c == 0) tv.setMaxWidth(Ux.dp(this, 190));
            row.addView(tv);
        }
        return row;
    }
    static java.util.List<String[]> withTotal(ReportsActivity.Report r) { java.util.List<String[]> l = new java.util.ArrayList<>(r.rows); if (r.total != null) l.add(r.total); return l; }
    static String asText(ReportsActivity.Report r) {
        StringBuilder s = new StringBuilder(r.title).append('\n').append(r.sub).append("\n\n");
        for (String[] row : withTotal(r)) { s.append(String.join(" | ", row)).append('\n'); }
        return s.toString();
    }
}
