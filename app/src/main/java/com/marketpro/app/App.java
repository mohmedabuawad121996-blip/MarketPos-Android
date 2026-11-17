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
    }
}
