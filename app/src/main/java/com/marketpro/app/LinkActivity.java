package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.market.Store;

/** الربط مع نسخة الكمبيوتر: فتح خادم البرنامج على الشبكة (ملخص اليوم والبحث في الأصناف من الكمبيوتر مباشرة) */
public class LinkActivity extends Activity {
    WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "الربط مع نسخة الكمبيوتر", false);
        body.addView(Ux.text(this, "في برنامج الكمبيوتر: إعدادات النظام ← الربط مع نسخة الكمبيوتر ← تشغيل الخادم، ثم اكتب العنوان الظاهر هناك (مثل 192.168.1.10:8085). الهاتف والكمبيوتر على نفس شبكة الواي فاي.", 12, false, Ux.MUTED));
        LinearLayout r = Ux.row(this);
        EditText url = Ux.field(this, "192.168.1.10:8085", false); url.setText(Store.db.get("pcLink", "")); r.addView(url, Ux.weight(1));
        android.widget.Button go = Ux.button(this, "فتح", Ux.PRIMARY, v -> open(url.getText().toString()));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ux.dp(this, 80), -2); gl.setMarginStart(Ux.dp(this, 6)); r.addView(go, gl);
        Ux.add(body, r, 6);
        web = new WebView(this); WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 6); body.addView(web, lp);
        if (!Store.db.get("pcLink", "").isEmpty()) open(Store.db.get("pcLink", ""));
    }
    void open(String u) {
        u = u.trim(); if (u.isEmpty()) return;
        if (!u.startsWith("http")) u = "http://" + u;
        Store.db.set("pcLink", u.replaceFirst("^https?://", "")); Store.save();
        web.loadUrl(u);
    }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
