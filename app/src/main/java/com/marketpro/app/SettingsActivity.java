package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.market.License;
import com.market.Session;
import com.market.Store;

/** الإعدادات: بيانات المحل، العملة والكسور، البيع بأقل من التكلفة، كلمة المرور، التفعيل */
public class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "الإعدادات", true);
        boolean admin = Session.admin();
        LinearLayout store = Ux.card(this); body.addView(store);
        store.addView(Ux.text(this, "بيانات المحل", 16, true, Ux.PRIMARY));
        EditText name = f(store, "اسم المحل", "storeName", false), addr = f(store, "العنوان", "address", false), phone = f(store, "الهاتف", "phone", false),
                tax = f(store, "الرقم الضريبي", "taxNo", false), cur = f(store, "رمز العملة", "currency", false), dec = f(store, "عدد الخانات العشرية (0-3)", "decimals", true),
                foot = f(store, "تذييل الفاتورة", "footer", false);
        CheckBox below = new CheckBox(this); below.setText("السماح بالبيع بأقل من التكلفة"); below.setChecked(Store.db.getB("allowBelowCost", false)); Ux.add(store, below, 6);
        CheckBox taxOn = new CheckBox(this); taxOn.setText("تفعيل الضريبة"); taxOn.setChecked(Store.db.getB("taxEnabled", true)); store.addView(taxOn);
        CheckBox taxIn = new CheckBox(this); taxIn.setText("الأسعار شاملة الضريبة"); taxIn.setChecked(Store.db.getB("taxInclusive", true)); store.addView(taxIn);
        EditText rate = f(store, "نسبة الضريبة %", "taxRate", true);
        CheckBox neg = new CheckBox(this); neg.setText("السماح بالبيع والمخزون صفر (سالب)"); neg.setChecked(Store.db.getB("allowNegative", true)); store.addView(neg);
        if (admin) Ux.add(store, Ux.button(this, "حفظ الإعدادات", Ux.PRIMARY, v -> {
            put("storeName", name); put("address", addr); put("phone", phone); put("taxNo", tax); put("currency", cur); put("footer", foot);
            int d; try { d = Math.max(0, Math.min(3, Integer.parseInt(dec.getText().toString().trim()))); } catch (Exception e) { d = 3; }
            Store.db.set("decimals", String.valueOf(d));
            Store.db.set("allowBelowCost", below.isChecked() ? "1" : "0"); Store.db.set("allowNegative", neg.isChecked() ? "1" : "0");
            Store.db.set("taxEnabled", taxOn.isChecked() ? "1" : "0"); Store.db.set("taxInclusive", taxIn.isChecked() ? "1" : "0"); put("taxRate", rate);
            Store.save(); Ux.toast(this, "تم الحفظ");
        }), 10);
        else store.addView(Ux.text(this, "تعديل الإعدادات للمدير فقط", 13, false, Ux.MUTED));

        LinearLayout pass = Ux.card(this); Ux.add(body, pass, 12);
        pass.addView(Ux.text(this, "تغيير كلمة المرور — " + Session.name(), 16, true, Ux.PRIMARY));
        EditText oldP = Ux.field(this, "كلمة المرور الحالية", false), newP = Ux.field(this, "كلمة المرور الجديدة", false), newP2 = Ux.field(this, "تأكيد كلمة المرور", false);
        for (EditText e : new EditText[]{oldP, newP, newP2}) { e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); Ux.add(pass, e, 6); }
        Ux.add(pass, Ux.button(this, "تغيير", Ux.BLUE, v -> {
            if (Session.user == null) return;
            if (!Session.verify(oldP.getText().toString(), Session.user.passHash)) { Ux.alert(this, "كلمة المرور الحالية غير صحيحة"); return; }
            String n = newP.getText().toString();
            if (n.length() < 4) { Ux.alert(this, "كلمة المرور قصيرة (4 أحرف على الأقل)"); return; }
            if (!n.equals(newP2.getText().toString())) { Ux.alert(this, "التأكيد لا يطابق"); return; }
            Session.user.passHash = Session.hash(n); Store.save(); oldP.setText(""); newP.setText(""); newP2.setText(""); Ux.toast(this, "تم تغيير كلمة المرور");
        }), 8);

        LinearLayout lic = Ux.card(this); Ux.add(body, lic, 12);
        lic.addView(Ux.text(this, "الترخيص", 16, true, Ux.PRIMARY));
        lic.addView(Ux.text(this, License.isActivated() ? "النسخة مفعّلة ✓" : License.status(), 15, true, License.isActivated() ? Ux.GREEN : Ux.ORANGE));
        lic.addView(Ux.text(this, "الإصدار " + BuildConfig.VERSION_NAME + "  •  " + Store.engine(), 13, false, Ux.MUTED));
        if (!License.isActivated()) {
            Ux.add(lic, Ux.text(this, "رقم الجهاز (أرسله لنا للحصول على مفتاح التفعيل):", 13, false, Ux.MUTED), 8);
            android.widget.TextView id = Ux.text(this, License.machineId(), 20, true, Ux.TEXT); id.setTextIsSelectable(true); lic.addView(id);
            LinearLayout r = Ux.row(this);
            r.addView(Ux.button(this, "نسخ", Ux.TEAL, v -> { android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("id", License.machineId())); Ux.toast(this, "تم نسخ رقم الجهاز"); }), Ux.weight(1));
            android.view.View g = new android.view.View(this); r.addView(g, new LinearLayout.LayoutParams(Ux.dp(this, 6), 1));
            r.addView(Ux.button(this, "إرسال", Ux.BLUE, v -> Share.text(this, "رقم جهاز MarketPro", "طلب تفعيل MarketPro للهاتف\nرقم الجهاز: " + License.machineId())), Ux.weight(1));
            Ux.add(lic, r, 6);
            EditText key = Ux.field(this, "مفتاح التفعيل", false); Ux.add(lic, key, 8);
            Ux.add(lic, Ux.button(this, "تفعيل", Ux.GREEN, v -> {
                if (License.activate(key.getText().toString())) { Ux.alert(this, "تم تفعيل البرنامج بنجاح ✓"); recreate(); }
                else Ux.alert(this, "مفتاح التفعيل غير صحيح لهذا الجهاز");
            }), 6);
        }
    }
    EditText f(LinearLayout box, String label, String key, boolean num) {
        Ux.add(box, Ux.text(this, label, 13, false, Ux.MUTED), 6);
        EditText e = Ux.field(this, label, num); e.setText(Store.db.settings.getOrDefault(key, "")); e.setEnabled(Session.admin()); box.addView(e); return e;
    }
    void put(String k, EditText e) { Store.db.set(k, e.getText().toString().trim()); }
}
