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
        CheckBox neg = new CheckBox(this); neg.setText("السماح بالبيع والمخزون صفر (سالب)"); neg.setChecked(Store.db.getB("allowNegative", true)); store.addView(neg);
        if (admin) Ux.add(store, Ux.button(this, "حفظ الإعدادات", Ux.PRIMARY, v -> {
            put("storeName", name); put("address", addr); put("phone", phone); put("taxNo", tax); put("currency", cur); put("footer", foot);
            int d; try { d = Math.max(0, Math.min(3, Integer.parseInt(dec.getText().toString().trim()))); } catch (Exception e) { d = 3; }
            Store.db.set("decimals", String.valueOf(d));
            Store.db.set("allowBelowCost", below.isChecked() ? "1" : "0"); Store.db.set("allowNegative", neg.isChecked() ? "1" : "0");
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
        lic.addView(Ux.text(this, License.isActivated() ? "النسخة مفعّلة ✓" : "نسخة تجريبية — متبقٍ " + License.daysLeft() + " يوم", 15, true, License.isActivated() ? Ux.GREEN : Ux.ORANGE));
        lic.addView(Ux.text(this, "الإصدار " + BuildConfig.VERSION_NAME + "  •  " + Store.engine(), 13, false, Ux.MUTED));
    }
    EditText f(LinearLayout box, String label, String key, boolean num) {
        Ux.add(box, Ux.text(this, label, 13, false, Ux.MUTED), 6);
        EditText e = Ux.field(this, label, num); e.setText(Store.db.settings.getOrDefault(key, "")); e.setEnabled(Session.admin()); box.addView(e); return e;
    }
    void put(String k, EditText e) { Store.db.set(k, e.getText().toString().trim()); }
}
