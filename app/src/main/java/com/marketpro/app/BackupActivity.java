package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.market.Session;
import com.market.Store;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/** النسخ الاحتياطي: تصدير قاعدة البيانات ومشاركتها، واستيراد نسخة (من الكمبيوتر أو الهاتف) */
public class BackupActivity extends Activity {
    static final int PICK = 41;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "النسخ الاحتياطي", true);
        LinearLayout c1 = Ux.card(this); body.addView(c1);
        c1.addView(Ux.text(this, "حفظ نسخة ومشاركتها", 16, true, Ux.PRIMARY));
        c1.addView(Ux.text(this, "تُنشأ نسخة كاملة من البيانات (ملف .db) ويمكنك إرسالها إلى واتساب أو البريد أو Google Drive، أو نقلها إلى برنامج الكمبيوتر.", 13, false, Ux.MUTED));
        Ux.add(c1, Ux.button(this, "إنشاء نسخة ومشاركتها", Ux.PRIMARY, v -> export()), 8);
        LinearLayout c2 = Ux.card(this); Ux.add(body, c2, 12);
        c2.addView(Ux.text(this, "استعادة نسخة", 16, true, Ux.RED));
        c2.addView(Ux.text(this, "اختر ملف نسخة (.db) من الهاتف — مثلاً نسخة من برنامج الكمبيوتر. ستُستبدل كل البيانات الحالية (تُحفظ نسخة منها تلقائياً قبل الاستعادة).", 13, false, Ux.MUTED));
        Ux.add(c2, Ux.button(this, "اختيار ملف للاستعادة", Ux.RED, v -> {
            if (!Session.admin()) { Ux.alert(this, "الاستعادة للمدير فقط"); return; }
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
            startActivityForResult(i, PICK);
        }), 8);
    }

    void export() {
        Ux.run(this, () -> {
            try {
                File dir = new File(getCacheDir(), "share"); dir.mkdirs();
                Path out = Store.backupTo(dir.toPath(), "MarketPro");
                Share.share(this, out.toFile(), "application/octet-stream");
            } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        Ux.confirm(this, "سيتم استبدال كل البيانات الحالية بمحتوى الملف المختار. متابعة؟", () -> Ux.run(this, () -> {
            try {
                File dir = new File(getCacheDir(), "restore"); dir.mkdirs();
                File f = new File(dir, "restore.db"); f.delete();
                try (InputStream in = getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(f)) {
                    byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                }
                Store.restore(f.toPath());
                com.market.Ops.ensureWallets(); com.market.Ops.ensureWarehouses(); Session.ensureAdmin();
                f.delete();
                Session.user = null;
                Ux.toast(this, "تمت الاستعادة. سجّل الدخول من جديد");
                startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException("الملف غير صالح: " + e.getMessage(), e); }
        }));
    }
}
