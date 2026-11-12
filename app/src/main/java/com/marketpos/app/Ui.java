package com.marketpos.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared colors and small view helpers, matching the MarketPos dark theme. */
final class Ui {

    static final int BG = Color.parseColor("#121214");
    static final int HEADER = Color.parseColor("#33434F");
    static final int SECTION = Color.parseColor("#1A1B1F");
    static final int CARD = Color.parseColor("#1E1F22");
    static final int DIVIDER = Color.parseColor("#2A2B2F");
    static final int RED = Color.parseColor("#E53935");
    static final int ACCENT = Color.parseColor("#3C8DBC");

    private Ui() {
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout vertical(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static TextView header(Context c, String title) {
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(22);
        t.setTypeface(null, Typeface.BOLD);
        t.setBackgroundColor(HEADER);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 16), dp(c, 20), dp(c, 16), dp(c, 20));
        return t;
    }
}
