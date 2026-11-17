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
        list.removeAllViews(); String group = null;
        boolean cost = Session.cap("cap:cost");
        int[] colors = {Ux.PRIMARY, Ux.GREEN, Ux.PURPLE, Ux.DARK, Ux.BLUE, Ux.TEAL, Ux.ORANGE, Ux.RED, Ux.DARK, Ux.PURPLE}; int gi = -1;
        for (ReportApi.Def d : defs) {
            if (!q.isEmpty() && !d.name.contains(q) && !d.group.contains(q)) continue;
            if (!cost && (d.name.contains("أرباح") || d.name.contains("الأرباح") || d.group.contains("الأرباح") || d.group.contains("المحاسبة") || d.group.contains("رأس المال"))) continue;
            if (!d.group.equals(group)) { group = d.group; gi++; Ux.add(list, Ux.text(this, group, 16, true, Ux.TEXT), 14); }
            int c = colors[Math.max(0, gi) % colors.length];
            Ux.add(list, Ux.button(this, d.name, c, v -> startActivity(new Intent(this, ReportViewActivity.class).putExtra("index", d.index))), 6);
        }
    }
}
