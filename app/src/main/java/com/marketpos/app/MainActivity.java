package com.marketpos.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    /** {title, icon, screen}. A null screen means the module is not built yet. */
    private static final Object[][] MENU = {
            {"المشتريات", "📦", null},
            {"المبيعات", "🛒", SaleActivity.class},
            {"الموردين", "🤝", null},
            {"العملاء", "👤", null},
            {"المصروفات", "💳", null},
            {"الصندوق", "🔐", null},
            {"الاستعلامات", "📊", ReportsActivity.class},
            {"المخزون", "🏬", null},
            {"النسخ الاحتياطي", "☁️", BackupActivity.class},
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "MarketPos"));

        LinearLayout grid = Ui.vertical(this);
        grid.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));

        for (int i = 0; i < MENU.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(tile(MENU[i]));
            if (i + 1 < MENU.length) {
                row.addView(tile(MENU[i + 1]));
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout tile(final Object[] item) {
        final String title = (String) item[0];
        final Class<?> screen = (Class<?>) item[2];

        LinearLayout tile = Ui.vertical(this);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 28));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.CARD);
        bg.setStroke(Ui.dp(this, 1), Color.parseColor("#3A3B3F"));
        bg.setCornerRadius(Ui.dp(this, 14));
        tile.setBackground(bg);

        TextView icon = new TextView(this);
        icon.setText((String) item[1]);
        icon.setTextSize(40);
        icon.setGravity(Gravity.CENTER);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setGravity(Gravity.CENTER);

        tile.addView(icon);
        tile.addView(name);

        int m = Ui.dp(this, 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(m, m, m, m);
        tile.setLayoutParams(lp);

        tile.setOnClickListener(v -> {
            if (screen != null) {
                startActivity(new Intent(this, screen));
            } else {
                Toast.makeText(this, title + " - قريباً", Toast.LENGTH_SHORT).show();
            }
        });
        return tile;
    }
}
