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
        build();
    }
    void build() {
        ScrollView sv = new ScrollView(this); sv.setFillViewport(true);
        sv.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{Ux.HEADER, Ux.shade(Ux.PRIMARY, -0.1)}));
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setGravity(Gravity.CENTER); outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = Ux.dp(this, 22); outer.setPadding(p, p, p, p);
        LinearLayout card = Ux.card(this); card.setGravity(Gravity.CENTER_HORIZONTAL); int cp = Ux.dp(this, 22); card.setPadding(cp, cp, cp, cp);
        card.setBackground(Ux.round(Ux.CARD, 18, this)); card.setElevation(Ux.dp(this, 8));
        android.widget.ImageView logo = new android.widget.ImageView(this); logo.setImageDrawable(Sidebar.logo(this, Ux.dp(this, 72))); card.addView(logo, new LinearLayout.LayoutParams(Ux.dp(this, 72), Ux.dp(this, 72)));
        android.widget.TextView store = Ux.text(this, Store.db.get("storeName", "سوبر ماركت"), 22, true, Ux.TEXT); store.setGravity(Gravity.CENTER);
        Ux.add(card, store, 10);
        android.widget.TextView hi = Ux.text(this, "MarketPro - نظام محاسبة السوبر ماركت", 13, false, Ux.MUTED); hi.setGravity(Gravity.CENTER); card.addView(hi);
        user = Ux.field(this, "اسم المستخدم", false); user.setText(Store.db.get("lastUser", "admin")); Ux.add(card, user, 18);
        user.setCompoundDrawablesRelative(Icons.get("user", Ux.dp(this, 20), Ux.MUTED), null, null, null); user.setCompoundDrawablePadding(Ux.dp(this, 8));
        pass = Ux.field(this, "كلمة المرور", false); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); Ux.add(card, pass, 10);
        pass.setCompoundDrawablesRelative(Icons.get("lock", Ux.dp(this, 20), Ux.MUTED), null, null, null); pass.setCompoundDrawablePadding(Ux.dp(this, 8));
        pass.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE); pass.setOnEditorActionListener((v, id, e) -> { login(); return true; });
        android.widget.Button go = Ux.button(this, "دخول", Ux.PRIMARY, v -> login()); go.setCompoundDrawablesRelative(Icons.get("login", Ux.dp(this, 18), Color.WHITE), null, null, null);
        Ux.add(card, go, 16);
        long left = License.daysLeft();
        android.widget.TextView lic = Ux.text(this, License.isActivated() ? "النسخة مفعّلة" : "نسخة تجريبية - متبقٍ " + left + " يوم", 12, false, Ux.MUTED);
        lic.setGravity(Gravity.CENTER); Ux.add(card, lic, 14);
        boolean noPass = false; for (User u : Store.db.users) if (u.username.equals("admin") && u.passHash.isEmpty()) noPass = true;
        if (noPass) { android.widget.TextView t = Ux.text(this, "أول دخول: اسم المستخدم admin بدون كلمة مرور", 12, false, Ux.ORANGE); t.setGravity(Gravity.CENTER); Ux.add(card, t, 6); }
        // تذييل البطاقة: اختيار الثيم (مثل نافذة الدخول في الكمبيوتر)
        LinearLayout foot = Ux.row(this); foot.setGravity(Gravity.CENTER);
        android.widget.ImageView pal = new android.widget.ImageView(this); pal.setImageDrawable(Icons.get("palette", Ux.dp(this, 18), Ux.MUTED)); foot.addView(pal);
        android.widget.TextView th = Ux.text(this, "الثيم: " + Ux.themeName(), 13, true, Ux.PRIMARY); th.setPadding(Ux.dp(this, 6), Ux.dp(this, 6), Ux.dp(this, 6), Ux.dp(this, 6)); foot.addView(th);
        foot.setOnClickListener(v -> SettingsActivity.pickTheme(this, this::build));
        Ux.add(card, foot, 12);
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
        sv.addView(outer); setContentView(sv);
        getWindow().setStatusBarColor(Ux.HEADER);
    }

    void login() {
        User u = Session.login(user.getText().toString(), pass.getText().toString());
        if (u == null) { Ux.alert(this, "اسم المستخدم أو كلمة المرور غير صحيحة"); return; }
        startActivity(new Intent(this, HomeActivity.class)); finish();
    }
}
