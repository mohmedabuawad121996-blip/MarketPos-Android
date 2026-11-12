package com.marketpos.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Sign in to Google Drive and upload a copy of the local database. */
public class BackupActivity extends AppCompatActivity {

    private static final String DB_NAME = "marketpos.db";

    private TextView status;

    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    GoogleSignInAccount account = GoogleSignIn
                            .getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    showStatus(account);
                } catch (ApiException e) {
                    status.setText("فشل تسجيل الدخول، الرمز: " + e.getStatusCode());
                }
            });

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "النسخ الاحتياطي"));

        LinearLayout body = Ui.vertical(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(0, 0, 0, Ui.dp(this, 12));
        body.addView(status);

        body.addView(button("تسجيل الدخول إلى Google Drive",
                v -> signInLauncher.launch(DriveHelper.signInClient(this).getSignInIntent())));
        body.addView(button("نسخ احتياطي الآن", v -> backupNow()));

        root.addView(body);
        setContentView(root);
        showStatus(DriveHelper.account(this));
    }

    private void showStatus(GoogleSignInAccount account) {
        status.setText(account == null ? "غير مسجل الدخول إلى Google Drive"
                : "مسجل الدخول: " + account.getEmail());
    }

    private void backupNow() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Toast.makeText(this, "سجّل الدخول إلى Google Drive أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        File db = getDatabasePath(DB_NAME);
        if (!db.exists()) {
            Toast.makeText(this, "لا توجد قاعدة بيانات بعد", Toast.LENGTH_SHORT).show();
            return;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(new Date());
        File copy = new File(getCacheDir(), "marketpos-backup-" + stamp + ".db");
        try {
            Files.copy(db.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Toast.makeText(this, "تعذر تجهيز النسخة: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        DriveHelper.upload(this, account, copy, "application/x-sqlite3");
    }

    private TextView button(String text, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.ACCENT);
        bg.setCornerRadius(Ui.dp(this, 12));
        b.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 12));
        b.setLayoutParams(lp);

        b.setOnClickListener(listener);
        return b;
    }
}
