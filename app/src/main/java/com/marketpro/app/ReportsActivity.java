package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.market.ReportApi;
import com.market.Session;

import java.util.List;

/** مركز التقارير: كل تقارير نسخة الكمبيوتر مجمّعة حسب القسم مع بحث */
public class ReportsActivity extends Activity {
    LinearLayout list; List<ReportApi.Def> defs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "التقارير", false);
        EditText search = Ux.field(this, "ابحث عن تقرير…", false); body.addView(search);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); sv.addView(list);
        body.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        defs = ReportApi.defs();
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable e) { fill(e.toString().trim()); } });
        fill("");
    }
    void fill(String q) {
        list.removeAllViews(); String group = null; LinearLayout box = null;
        boolean cost = Session.cap("cap:cost");
        for (ReportApi.Def d : defs) {
            if (!q.isEmpty() && !d.name.contains(q) && !d.group.contains(q)) continue;
            if (!cost && (d.name.contains("أرباح") || d.name.contains("الأرباح") || d.group.contains("الأرباح") || d.group.contains("المحاسبة") || d.group.contains("رأس المال"))) continue;
            if (!d.group.equals(group)) { group = d.group;
                box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setBackground(Ux.outline(Ux.CARD, Ux.BORDER, 12, this)); box.setClipToOutline(true);
                android.widget.TextView h = Ux.text(this, group, 15, true, Ux.TEXT); int p = Ux.dp(this, 12); h.setPadding(p, Ux.dp(this, 9), p, Ux.dp(this, 9)); h.setBackgroundColor(Ux.HEAD);
                h.setCompoundDrawablesRelative(Icons.get(icon(group), Ux.dp(this, 18), Ux.PRIMARY), null, null, null); h.setCompoundDrawablePadding(Ux.dp(this, 8));
                box.addView(h); Ux.add(list, box, list.getChildCount() == 0 ? 0 : 12); }
            android.view.View r = Ux.listRow(this, d.name, null, null, 0);
            r.setOnClickListener(v -> startActivity(new Intent(this, ReportViewActivity.class).putExtra("index", d.index)));
            box.addView(r);
        }
    }
    static String icon(String g) {
        return g.contains("المبيعات") ? "cart" : g.contains("الأرباح") ? "chart" : g.contains("المخزون") ? "tag" : g.contains("الصندوق") ? "wallet" : g.contains("العملاء") ? "users"
                : g.contains("الموردون") ? "truck" : g.contains("الضرائب") ? "percent" : g.contains("المصروفات") ? "cash" : g.contains("المحاسبة") ? "doc" : "chart";
    }
}
