package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.market.Session;

/** قائمة قسم من أقسام البرنامج (نفس قائمة الكمبيوتر) */
public class MenuActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Menus.Section s = Menus.all().get(getIntent().getIntExtra("sec", 0));
        LinearLayout body = Ux.screen(this, s.icon + "  " + s.title, true);
        int n = 0;
        for (Menus.Item it : s.items) {
            if (!Session.admin() && !Session.can(it.title)) continue;
            Ux.add(body, Ux.button(this, it.title, s.color, v -> Ux.run(this, () -> it.act.run(this))), n++ == 0 ? 0 : 8);
        }
        if (n == 0) body.addView(Ux.text(this, "لا توجد شاشات مسموحة لك في هذا القسم", 15, true, Ux.MUTED));
    }
}
