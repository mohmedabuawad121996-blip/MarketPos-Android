package com.marketpro.app;

import android.app.Application;

import com.market.Store;
import com.market.Ui;

/** تهيئة البيانات: نفس قاعدة SQLite التي يستخدمها برنامج الحاسوب، في مجلد التطبيق */
public class App extends Application {
    public static volatile boolean loaded;
    public static volatile Throwable loadError;

    @Override public void onCreate() {
        super.onCreate();
        Store.dataDir = getFilesDir().toPath().resolve("MarketPro");
        Ui.teller = msg -> android.util.Log.e("MarketPro", msg);
        android.content.SharedPreferences sp = getSharedPreferences("mp_license", MODE_PRIVATE);
        com.market.License.prefs = new com.market.License.Prefs() {
            public String get(String k) { return sp.getString(k, null); }
            public void put(String k, String v) { sp.edit().putString(k, v).apply(); }
        };
        String id = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        com.market.License.rawDeviceId = id == null ? "" : id;
    }
}
