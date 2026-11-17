package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.market.License;
import com.market.Model.User;
import com.market.Session;
import com.market.Store;

/** تسجيل الدخول: نفس المستخدمين وكلمات المرور في برنامج الحاسوب (أول دخول: admin بدون كلمة مرور) */
public class LoginActivity extends Activity {
    EditText user, pass;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(Ux.BG);
        LinearLayout outer = new LinearLayout(this); outer.setGravity(Gravity.CENTER); outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = Ux.dp(this, 20); outer.setPadding(p, p, p, p);
        LinearLayout card = Ux.card(this); card.setGravity(Gravity.CENTER_HORIZONTAL); int cp = Ux.dp(this, 22); card.setPadding(cp, cp, cp, cp);
        android.widget.TextView logo = Ux.text(this, "🛒", 34, false, Color.WHITE); logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ux.round(Ux.BLUE, 18, this)); card.addView(logo, new LinearLayout.LayoutParams(Ux.dp(this, 70), Ux.dp(this, 70)));
        android.widget.TextView store = Ux.text(this, Store.db.get("storeName", "سوبر ماركت"), 24, true, Ux.TEXT); store.setGravity(Gravity.CENTER);
        Ux.add(card, store, 10);
        android.widget.TextView hi = Ux.text(this, "سجّل الدخول للمتابعة", 14, false, Ux.MUTED); hi.setGravity(Gravity.CENTER); card.addView(hi);
        user = Ux.field(this, "اسم المستخدم", false); user.setText(Store.db.get("lastUser", "admin")); Ux.add(card, user, 18);
        pass = Ux.field(this, "كلمة المرور", false); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); Ux.add(card, pass, 10);
        Ux.add(card, Ux.button(this, "تسجيل الدخول", Ux.PRIMARY, v -> login()), 16);
        long left = License.daysLeft();
        android.widget.TextView lic = Ux.text(this, License.isActivated() ? "النسخة مفعّلة" : "نسخة تجريبية - متبقٍ " + left + " يوم", 12, false, Ux.MUTED);
        lic.setGravity(Gravity.CENTER); Ux.add(card, lic, 14);
        boolean noPass = false; for (User u : Store.db.users) if (u.username.equals("admin") && u.passHash.isEmpty()) noPass = true;
        if (noPass) { android.widget.TextView t = Ux.text(this, "أول دخول: اسم المستخدم admin بدون كلمة مرور", 12, false, Ux.ORANGE); t.setGravity(Gravity.CENTER); Ux.add(card, t, 6); }
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
        sv.addView(outer); setContentView(sv);
    }

    void login() {
        User u = Session.login(user.getText().toString(), pass.getText().toString());
        if (u == null) { Ux.alert(this, "اسم المستخدم أو كلمة المرور غير صحيحة"); return; }
        startActivity(new Intent(this, HomeActivity.class)); finish();
    }
}
