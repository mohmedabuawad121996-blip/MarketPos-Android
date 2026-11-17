package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.market.Ops;
import com.market.Session;
import com.market.Store;

/** شاشة البداية: تحميل قاعدة البيانات في الخلفية ثم الانتقال لتسجيل الدخول */
public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER);
        l.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFF2563EB, 0xFF14B8A6}));
        l.addView(Ux.text(this, "MarketPro", 34, true, Color.WHITE));
        l.addView(Ux.text(this, "نظام محاسبة السوبر ماركت", 16, false, Color.WHITE));
        ProgressBar pb = new ProgressBar(this); l.addView(pb, Ux.gap(this, 24));
        setContentView(l);
        if (App.loaded) { go(); return; }
        new Thread(() -> {
            try {
                Store.load(); Ops.ensureWallets(); Ops.ensureWarehouses(); Session.ensureAdmin(); Ux.applyTheme();
                App.loaded = true;
            } catch (Throwable t) { App.loadError = t; }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (App.loadError != null) Ux.alert(this, "تعذّر تحميل البيانات: " + App.loadError.getMessage());
                else go();
            });
        }, "load").start();
    }
    void go() { startActivity(new Intent(this, LoginActivity.class)); finish(); }
}
