package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.market.JoFotara;
import com.market.License;
import com.market.Model.Invoice;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** إعدادات النظام: نفس أقسام إعدادات برنامج الكمبيوتر */
public class SettingsActivity extends Activity {
    static final String[][] SECS = {{"general", "الإعدادات العامة"}, {"tax", "الضرائب"}, {"extras", "الميزان والنقاط"}, {"printer", "إعدادات الطابعة"}, {"look", "المظهر والخط والثيمات"},
            {"password", "تغيير كلمة المرور"}, {"activation", "تفعيل البرنامج"}, {"jof", "الفوترة الوطنية (JoFotara)"}, {"reset", "استعادة الإعدادات الافتراضية"},
            {"close", "إقفال السنة المالية"}, {"about", "حول البرنامج"}};
    final Map<String, View> fields = new LinkedHashMap<>(); LinearLayout body; String sec;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sec = getIntent().getStringExtra("sec");
        String title = "الإعدادات"; if (sec != null) for (String[] s : SECS) if (s[0].equals(sec)) title = s[1];
        body = Ux.screen(this, title, true);
        if (sec == null) {
            for (String[] s : SECS) { if (!Session.admin() && !s[0].equals("password") && !s[0].equals("about") && !s[0].equals("activation")) continue;
                Ux.add(body, Ux.button(this, s[1], Ux.PRIMARY, v -> startActivity(new Intent(this, SettingsActivity.class).putExtra("sec", s[0]))), 8); }
            Ux.add(body, Ux.button(this, "إدارة المحافظ", Ux.DARK, v -> startActivity(new Intent(this, ListsActivity.class).putExtra("mode", "wallet"))), 8);
            Ux.add(body, Ux.button(this, "المستخدمون والصلاحيات", Ux.DARK, v -> { if (Session.admin()) startActivity(new Intent(this, UsersActivity.class)); else Ux.alert(this, "للمدير فقط"); }), 8);
            Ux.add(body, Ux.button(this, "النسخ الاحتياطي", Ux.DARK, v -> startActivity(new Intent(this, BackupActivity.class))), 8);
            Ux.add(body, Ux.button(this, "الربط مع نسخة الكمبيوتر", Ux.DARK, v -> startActivity(new Intent(this, LinkActivity.class))), 8);
            return;
        }
        boolean adminSec = !sec.equals("password") && !sec.equals("about") && !sec.equals("activation");
        if (adminSec && !Session.admin()) { body.addView(Ux.text(this, "هذه الإعدادات للمدير فقط", 15, true, Ux.MUTED)); return; }
        switch (sec) {
            case "general": general(); break; case "tax": tax(); break; case "extras": extras(); break; case "printer": printer(); break; case "look": look(); break;
            case "password": password(); break; case "activation": activation(); break; case "jof": jof(); break; case "reset": reset(); break;
            case "close": closeYear(); break; default: about();
        }
    }

    // ------------------------------------------------ أدوات النموذج
    LinearLayout card(String t) { LinearLayout c = Ux.card(this); Ux.add(body, c, body.getChildCount() == 0 ? 0 : 12); if (t != null) c.addView(Ux.text(this, t, 16, true, Ux.PRIMARY)); return c; }
    EditText f(LinearLayout c, String label, String key, boolean num) { return f(c, label, key, num, ""); }
    EditText f(LinearLayout c, String label, String key, boolean num, String def) {
        Ux.add(c, Ux.text(this, label, 13, false, Ux.MUTED), 6);
        EditText e = Ux.field(this, label, num); e.setText(Store.db.get(key, def)); c.addView(e); fields.put(key, e); return e;
    }
    CheckBox cb(LinearLayout c, String label, String key, boolean def) {
        CheckBox x = new CheckBox(this); x.setText(label); x.setTextSize(15 * Ux.FS); x.setChecked(Store.db.getB(key, def)); c.addView(x); fields.put(key, x); return x;
    }
    void saveBtn(LinearLayout c, Runnable extra) {
        Ux.add(c, Ux.button(this, "حفظ", Ux.PRIMARY, v -> {
            for (Map.Entry<String, View> e : fields.entrySet()) {
                if (e.getValue() instanceof CheckBox) Store.db.set(e.getKey(), ((CheckBox) e.getValue()).isChecked() ? "1" : "0");
                else Store.db.set(e.getKey(), ((EditText) e.getValue()).getText().toString().trim());
            }
            if (extra != null) extra.run();
            Store.save(); Ux.toast(this, "تم الحفظ");
        }), 12);
    }

    // ------------------------------------------------ الأقسام
    void general() {
        LinearLayout c = card("بيانات المحل");
        f(c, "اسم المحل", "storeName", false); f(c, "العنوان", "address", false); f(c, "الهاتف", "phone", false); f(c, "رمز العملة", "currency", false, "د.أ");
        f(c, "تذييل الفاتورة", "footer", false);
        LinearLayout s = card("البيع والمخزون");
        EditText dec = f(s, "عدد الخانات العشرية (0-3)", "decimals", true, "3");
        cb(s, "السماح بالبيع بأقل من التكلفة", "allowBelowCost", false); cb(s, "السماح بالبيع والمخزون صفر (سالب)", "allowNegative", true);
        cb(s, "السماح بإرجاع صنف بدون فاتورة أصلية", "allowFreeReturn", false); cb(s, "تفعيل الورديات", "shiftsEnabled", true);
        f(s, "التنبيه قبل انتهاء الصلاحية (أيام)", "expiryDays", true, "30"); f(s, "رمز الدولة للواتساب", "waCountry", true, "962");
        saveBtn(s, () -> { int d; try { d = Math.max(0, Math.min(3, Integer.parseInt(dec.getText().toString().trim()))); } catch (Exception e) { d = 3; } Store.db.set("decimals", String.valueOf(d)); });
    }
    void tax() {
        LinearLayout c = card("الضريبة العامة على المبيعات");
        cb(c, "تفعيل الضريبة", "taxEnabled", true); cb(c, "الأسعار شاملة الضريبة", "taxInclusive", true);
        f(c, "نسبة الضريبة %", "taxRate", true, "16"); f(c, "الرقم الضريبي للمحل", "taxNo", false);
        saveBtn(c, null);
    }
    void extras() {
        LinearLayout s = card("باركود الميزان");
        cb(s, "قراءة باركود الميزان (يبدأ بـ 2)", "scaleOn", true);
        f(s, "بادئات الميزان", "scalePrefixes", false, "20,21,22,23,24,25,26,27,28,29"); f(s, "عدد أرقام رقم الصنف (4-6)", "scaleCodeLen", true, "5");
        CheckBox byPrice = new CheckBox(this); byPrice.setText("الباركود يحمل السعر (بدل الوزن)"); byPrice.setChecked("price".equals(Store.db.get("scaleMode", "weight"))); s.addView(byPrice);
        f(s, "الخانات العشرية في الباركود", "scaleDecimals", true, "3");
        LinearLayout p = card("نقاط الولاء");
        cb(p, "تفعيل نقاط العملاء", "pointsOn", true); f(p, "نقطة لكل (مبلغ مشتريات)", "pointsPer", true, "1"); f(p, "قيمة النقطة", "pointValue", true, "0.01");
        f(p, "أقل عدد نقاط للاستبدال", "pointsMin", true, "100");
        saveBtn(p, () -> Store.db.set("scaleMode", byPrice.isChecked() ? "price" : "weight"));
    }
    void printer() {
        LinearLayout c = card("طابعة الإيصالات (بلوتوث)");
        TextView cur = Ux.text(this, Printer.mac().isEmpty() ? "لم تُحدَّد طابعة" : "الطابعة: " + Printer.name() + "  (" + Printer.mac() + ")", 15, true, Ux.TEXT); c.addView(cur);
        Ux.add(c, Ux.button(this, "اختيار الطابعة", Ux.BLUE, v -> Printer.pick(this, this::recreate)), 8);
        CheckBox p58 = new CheckBox(this); p58.setText("ورق 58 مم (بدل 80 مم)"); p58.setChecked("58".equals(Store.db.get("paper", "80"))); Ux.add(c, p58, 8);
        cb(c, "طباعة تلقائية بعد حفظ الفاتورة", "autoPrint", false);
        f(c, "عدد النسخ", "copies", true, "1");
        saveBtn(c, () -> Store.db.set("paper", p58.isChecked() ? "58" : "80"));
        Ux.add(c, Ux.button(this, "طباعة تجريبية", Ux.TEAL, v -> Printer.printText(this, Store.db.get("storeName", "MarketPro"), "طباعة تجريبية من MarketPro\nالعربية: ١٢٣ — 123\n" + Ui.dt(java.time.LocalDateTime.now()))), 8);
        c.addView(Ux.text(this, "اقرن الطابعة أولاً من إعدادات البلوتوث في الهاتف (الرمز غالباً 0000 أو 1234). تعمل مع طابعات ESC/POS الحرارية الشائعة.", 12, false, Ux.MUTED));
        LinearLayout l = card("ملصقات الباركود");
        l.addView(Ux.text(this, "تُطبع كملف PDF من: المخزون ← طباعة ملصقات الباركود", 13, false, Ux.MUTED));
    }
    void look() {
        LinearLayout c = card("الثيم (نفس ثيمات برنامج الكمبيوتر)");
        String cur = Ux.themeName();
        for (String t : Ux.THEMES) Ux.add(c, themeCard(this, t, t.equals(cur), () -> { Store.db.set("theme", t); Store.save(); Ux.applyTheme(); recreate(); }), 8);
        LinearLayout f = card("حجم الخط");
        String[] sizes = {"صغير", "عادي", "كبير", "كبير جداً"}; double[] vals = {0.9, 1.0, 1.15, 1.3};
        for (int i = 0; i < sizes.length; i++) { final int k = i;
            Ux.add(f, Ux.button(this, (Math.abs(Ux.FS - vals[i]) < 0.01 ? "✓ " : "") + sizes[i], Ux.TEAL, v -> { Store.db.set("phoneFont", String.valueOf(vals[k])); Store.save(); Ux.applyTheme(); recreate(); }), 6); }
    }
    /** بطاقة معاينة ثيم بألوانه: شريط جانبي + ترويسة + خلفية */
    static View themeCard(Activity a, String name, boolean selected, Runnable pick) {
        int[] saved = {Ux.PRIMARY, Ux.DARK, Ux.HEADER, Ux.SIDEBAR, Ux.SIDE_ITEM, Ux.SIDE_TEXT, Ux.BG, Ux.CARD, Ux.TEXT, Ux.MUTED, Ux.ROW, Ux.ROW_ALT, Ux.HEAD, Ux.SELECT, Ux.BORDER}; boolean dm = Ux.DARK_MODE;
        Ux.theme(name); int side = Ux.SIDEBAR, head = Ux.HEADER, bg = Ux.BG, card = Ux.CARD, prim = Ux.PRIMARY, txt = Ux.TEXT;
        Ux.PRIMARY = saved[0]; Ux.DARK = saved[1]; Ux.HEADER = saved[2]; Ux.SIDEBAR = saved[3]; Ux.SIDE_ITEM = saved[4]; Ux.SIDE_TEXT = saved[5]; Ux.BG = saved[6]; Ux.CARD = saved[7];
        Ux.TEXT = saved[8]; Ux.MUTED = saved[9]; Ux.ROW = saved[10]; Ux.ROW_ALT = saved[11]; Ux.HEAD = saved[12]; Ux.SELECT = saved[13]; Ux.BORDER = saved[14]; Ux.DARK_MODE = dm;
        LinearLayout r = new LinearLayout(a); r.setOrientation(LinearLayout.HORIZONTAL); r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setBackground(Ux.outline(bg, selected ? prim : Ux.BORDER, 10, a)); int pd = Ux.dp(a, selected ? 3 : 1); r.setPadding(pd, pd, pd, pd);
        View sb = new View(a); sb.setBackground(Ux.round(side, 8, a)); r.addView(sb, new LinearLayout.LayoutParams(Ux.dp(a, 34), Ux.dp(a, 58)));
        LinearLayout right = new LinearLayout(a); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(Ux.dp(a, 6), 0, Ux.dp(a, 6), 0);
        View hb = new View(a); hb.setBackground(Ux.round(head, 6, a)); right.addView(hb, new LinearLayout.LayoutParams(-1, Ux.dp(a, 16)));
        LinearLayout l2 = new LinearLayout(a); l2.setOrientation(LinearLayout.HORIZONTAL); l2.setGravity(Gravity.CENTER_VERTICAL); l2.setPadding(0, Ux.dp(a, 6), 0, 0);
        TextView t = Ux.text(a, (selected ? "✓ " : "") + name, 15, true, txt); l2.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        View dot = new View(a); dot.setBackground(Ux.round(prim, 9, a)); l2.addView(dot, new LinearLayout.LayoutParams(Ux.dp(a, 18), Ux.dp(a, 18)));
        View dot2 = new View(a); dot2.setBackground(Ux.outline(card, 0x22000000, 9, a)); LinearLayout.LayoutParams d2 = new LinearLayout.LayoutParams(Ux.dp(a, 18), Ux.dp(a, 18)); d2.setMarginStart(Ux.dp(a, 4)); l2.addView(dot2, d2);
        right.addView(l2); r.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        r.setOnClickListener(v -> pick.run());
        return r;
    }
    /** نافذة سريعة لاختيار الثيم (زر اللوحة في الرئيسية) */
    public static void pickTheme(Activity a, Runnable after) {
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL); int p = Ux.dp(a, 14); box.setPadding(p, p, p, p);
        android.widget.ScrollView sv = new android.widget.ScrollView(a); sv.addView(box);
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(a).setTitle("المظهر والثيمات").setView(sv).setNegativeButton("إغلاق", null).create();
        String cur = Ux.themeName();
        for (String t : Ux.THEMES) Ux.add(box, themeCard(a, t, t.equals(cur), () -> { Store.db.set("theme", t); Store.save(); Ux.applyTheme(); d.dismiss(); after.run(); }), 8);
        d.show();
    }
    void password() {
        LinearLayout pass = card("تغيير كلمة المرور — " + Session.name());
        EditText oldP = Ux.field(this, "كلمة المرور الحالية", false), newP = Ux.field(this, "كلمة المرور الجديدة", false), newP2 = Ux.field(this, "تأكيد كلمة المرور", false);
        for (EditText e : new EditText[]{oldP, newP, newP2}) { e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); Ux.add(pass, e, 6); }
        Ux.add(pass, Ux.button(this, "تغيير", Ux.BLUE, v -> {
            if (Session.user == null) return;
            if (!Session.verify(oldP.getText().toString(), Session.user.passHash)) { Ux.alert(this, "كلمة المرور الحالية غير صحيحة"); return; }
            String n = newP.getText().toString();
            if (n.length() < 4) { Ux.alert(this, "كلمة المرور قصيرة (4 أحرف على الأقل)"); return; }
            if (!n.equals(newP2.getText().toString())) { Ux.alert(this, "التأكيد لا يطابق"); return; }
            Session.user.passHash = Session.hash(n); Store.save(); oldP.setText(""); newP.setText(""); newP2.setText(""); Ux.toast(this, "تم تغيير كلمة المرور");
        }), 8);
    }
    void activation() {
        LinearLayout lic = card("الترخيص");
        lic.addView(Ux.text(this, License.isActivated() ? "النسخة مفعّلة ✓" : License.status(), 16, true, License.isActivated() ? Ux.GREEN : Ux.ORANGE));
        if (License.isActivated()) return;
        Ux.add(lic, Ux.text(this, "رقم الجهاز (أرسله لنا للحصول على مفتاح التفعيل):", 13, false, Ux.MUTED), 8);
        TextView id = Ux.text(this, License.machineId(), 22, true, Ux.TEXT); id.setTextIsSelectable(true); lic.addView(id);
        LinearLayout r = Ux.row(this);
        r.addView(Ux.button(this, "نسخ", Ux.TEAL, v -> { android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("id", License.machineId())); Ux.toast(this, "تم نسخ رقم الجهاز"); }), Ux.weight(1));
        View g = new View(this); r.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        r.addView(Ux.button(this, "إرسال", Ux.BLUE, v -> Share.text(this, "رقم جهاز MarketPro", "طلب تفعيل MarketPro للهاتف\nرقم الجهاز: " + License.machineId())), Ux.weight(1));
        Ux.add(lic, r, 6);
        EditText key = Ux.field(this, "مفتاح التفعيل", false); Ux.add(lic, key, 8);
        Ux.add(lic, Ux.button(this, "تفعيل", Ux.GREEN, v -> {
            if (License.activate(key.getText().toString())) { Ux.alert(this, "تم تفعيل البرنامج بنجاح ✓"); recreate(); }
            else Ux.alert(this, "مفتاح التفعيل غير صحيح لهذا الجهاز");
        }), 6);
    }
    void jof() {
        LinearLayout c = card("الربط مع نظام الفوترة الوطني الأردني");
        cb(c, "تفعيل الفوترة الوطنية", "jofEnabled", false); cb(c, "إرسال تلقائي بعد حفظ فاتورة البيع", "jofAuto", true);
        f(c, "Client ID", "jofClientId", false); EditText sec = f(c, "Secret Key", "jofSecret", false); sec.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        f(c, "الرقم الضريبي للمنشأة", "jofTin", false, Store.db.get("taxNo", "")); f(c, "تسلسل مصدر الدخل", "jofSeq", false);
        f(c, "اسم المنشأة المسجّل", "jofName", false, Store.db.get("storeName", "")); f(c, "رمز المحافظة (مثل JO-AM)", "jofCity", false, "JO-AM");
        CheckBox income = new CheckBox(this); income.setText("خاضع لضريبة الدخل فقط (غير مسجّل في المبيعات)"); income.setChecked("income".equals(Store.db.get("jofType", "sales"))); c.addView(income);
        f(c, "عنوان الخدمة", "jofUrl", false, JoFotara.DEFAULT_URL);
        saveBtn(c, () -> Store.db.set("jofType", income.isChecked() ? "income" : "sales"));
        LinearLayout p = card("الفواتير غير المرسلة");
        List<Invoice> pend = new ArrayList<>(); for (Invoice i : Store.db.invoices) if (JoFotara.applies(i) && !i.cancelled && !JoFotara.submitted(i)) pend.add(i);
        p.addView(Ux.text(this, "عدد الفواتير غير المقبولة بعد: " + pend.size(), 14, true, pend.isEmpty() ? Ux.GREEN : Ux.ORANGE));
        if (!pend.isEmpty()) Ux.add(p, Ux.button(this, "إرسال الكل الآن", Ux.BLUE, v -> {
            android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, null, "جاري الإرسال…", true);
            new Thread(() -> { int ok = 0; String last = ""; for (Invoice i : pend) { last = JoFotara.submit(i); if (JoFotara.submitted(i)) ok++; }
                int fok = ok; String fl = last; runOnUiThread(() -> { try { pd.dismiss(); } catch (Exception ignored) {} Ux.alert(this, "قُبلت " + fok + " من " + pend.size() + (fok < pend.size() ? "\nآخر رد: " + fl : "")); recreate(); }); }).start();
        }), 8);
        String last = Store.db.get("jofLastResponse", "");
        if (!last.isEmpty()) Ux.add(p, Ux.button(this, "آخر رد من النظام", Ux.TEAL, v -> Ux.alert(this, last.length() > 3000 ? last.substring(0, 3000) : last)), 8);
    }
    void reset() {
        LinearLayout c = card("استعادة الإعدادات الافتراضية");
        String[] names = {"إعدادات البيع", "المظهر", "الطباعة", "الميزان والنقاط", "بيانات المحل", "الربط والفوترة"};
        String[][] groups = {Store.G_SALES, Store.G_LOOK, Store.G_PRINT, Store.G_EXTRA, Store.G_STORE, Store.G_LINKS};
        List<CheckBox> bs = new ArrayList<>();
        for (String n : names) { CheckBox x = new CheckBox(this); x.setText(n); c.addView(x); bs.add(x); }
        Ux.add(c, Ux.button(this, "استعادة المختار", Ux.RED, v -> {
            List<String[]> g = new ArrayList<>(); for (int i = 0; i < bs.size(); i++) if (bs.get(i).isChecked()) g.add(groups[i]);
            if (g.isEmpty()) { Ux.alert(this, "اختر مجموعة واحدة على الأقل"); return; }
            Ux.confirm(this, "إرجاع الإعدادات المختارة للافتراضي؟ (البيانات لا تتأثر)", () -> { int n = Store.db.resetSettings(g); Store.save(); Ux.applyTheme(); Ux.alert(this, "تمت استعادة " + n + " إعداد"); });
        }), 10);
    }
    void closeYear() {
        LinearLayout c = card("إقفال السنة المالية");
        c.addView(Ux.text(this, "يحفظ أرشيفاً كاملاً للسنة، ثم يرحّل أرصدة الصناديق والعملاء والموردين وكميات المخزون كأرصدة افتتاحية ويحذف حركات السنة.\nخذ نسخة احتياطية قبل التنفيذ.", 14, false, Ux.TEXT));
        Ux.add(c, Ux.button(this, "إقفال السنة الآن", Ux.RED, v -> Ux.confirm(this, "إقفال السنة المالية؟ لا يمكن التراجع إلا باستعادة الأرشيف.", () ->
                Ux.input(this, "للتأكيد اكتب: إقفال", "إقفال", "", false, s -> { if (!s.trim().equals("إقفال")) { Ux.alert(this, "لم يتم التنفيذ"); return; }
                    final String[] out = new String[1];
                    if (Ux.run(this, () -> { try { out[0] = Ops.closeYear().getFileName().toString(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException(e.getMessage(), e); } }))
                        Ux.alert(this, "تم إقفال السنة ✓\nالأرشيف: " + out[0]); }))), 10);
    }
    void about() {
        LinearLayout c = card(null);
        TextView t = Ux.text(this, "MarketPro", 28, true, Ux.PRIMARY); t.setGravity(android.view.Gravity.CENTER); c.addView(t);
        TextView s = Ux.text(this, "نظام محاسبة ونقاط بيع للسوبر ماركت — نسخة الهاتف\nالإصدار " + BuildConfig.VERSION_NAME + "\n" + Store.engine() + "\n" + License.status(), 14, false, Ux.TEXT);
        s.setGravity(android.view.Gravity.CENTER); Ux.add(c, s, 8);
        String dev = "Mohammad Al-Dawayima", ph = "0789105104", mail = "Mohmed.abuawad121996@gmail.com";
        LinearLayout k = card("تطوير ودعم فني");
        k.addView(Ux.pair(this, "المطوّر", dev, Ux.TEXT)); k.addView(Ux.pair(this, "الهاتف / واتساب", ph, Ux.TEXT)); k.addView(Ux.pair(this, "البريد", mail, Ux.TEXT));
        LinearLayout r = Ux.row(this);
        r.addView(Ux.button(this, "واتساب", Ux.GREEN, v -> WA.send(this, ph, "مرحباً، أتواصل بخصوص برنامج MarketPro للهاتف " + BuildConfig.VERSION_NAME)), Ux.weight(1));
        View g = new View(this); r.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
        r.addView(Ux.button(this, "اتصال", Ux.BLUE, v -> startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + ph)))), Ux.weight(1));
        Ux.add(k, r, 8);
        TextView f = Ux.text(this, "© " + java.time.Year.now() + " " + dev + " — جميع الحقوق محفوظة", 12, false, Ux.MUTED); f.setGravity(android.view.Gravity.CENTER); Ux.add(body, f, 12);
    }
}
