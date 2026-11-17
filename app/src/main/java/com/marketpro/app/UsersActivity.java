package com.marketpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model.User;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** المستخدمون والصلاحيات */
public class UsersActivity extends Activity {
    static final String[][] CAPS = {
            {"cap:discount", "الخصم في فاتورة البيع"}, {"cap:price", "تعديل سعر الصنف في فاتورة البيع"}, {"cap:delete", "حذف سطر من الفاتورة"},
            {"cap:credit", "البيع الآجل (على الحساب)"}, {"cap:cost", "رؤية سعر التكلفة والأرباح"}, {"cap:cancel", "إلغاء / تعديل الفواتير المحفوظة"}};
    Ux.Adapter<User> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout body = Ux.screen(this, "المستخدمون والصلاحيات", false);
        body.addView(Ux.button(this, "+ مستخدم جديد", Ux.GREEN, v -> edit(null)));
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> edit(ad.getItem(i)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 8); body.addView(list, lp);
        ad.set(Store.db.users);
    }
    View row(User u, int pos) {
        return Ux.listRow(this, u.name, u.username + "  •  " + u.role + (u.lastLogin == null ? "" : "  •  آخر دخول " + Ui.dt(u.lastLogin)), u.active ? "فعّال" : "موقوف", u.active ? Ux.GREEN : Ux.MUTED);
    }
    void edit(User old) {
        boolean isNew = old == null; User u = isNew ? new User() : old;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pd = Ux.dp(this, 18); box.setPadding(pd, Ux.dp(this, 8), pd, 0);
        EditText name = f(box, "الاسم", u.name), user = f(box, "اسم الدخول", u.username), pass = f(box, isNew ? "كلمة المرور" : "كلمة مرور جديدة (اتركها فارغة لعدم التغيير)", "");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox admin = new CheckBox(this); admin.setText("مدير (كل الصلاحيات)"); admin.setChecked(isNew ? false : u.admin()); Ux.add(box, admin, 8);
        CheckBox active = new CheckBox(this); active.setText("الحساب فعّال"); active.setChecked(u.active); box.addView(active);
        Ux.add(box, Ux.text(this, "صلاحيات الكاشير", 14, true, Ux.PRIMARY), 8);
        Set<String> cur = isNew ? Session.defaults() : new HashSet<>(Session.permsOf(u));
        List<CheckBox> caps = new ArrayList<>();
        for (String[] c : CAPS) { CheckBox x = new CheckBox(this); x.setText(c[1]); x.setChecked(cur.contains(c[0])); x.setTag(c[0]); caps.add(x); box.addView(x); }
        Set<String> screens = new HashSet<>(cur); for (String[] c : CAPS) screens.remove(c[0]);
        List<String> allScreens = new ArrayList<>(); for (Menus.Section sc : Menus.all()) if (!sc.adminOnly) for (Menus.Item it : sc.items) if (!allScreens.contains(it.title)) allScreens.add(it.title);
        Ux.add(box, Ux.button(this, "الشاشات المسموحة للكاشير", Ux.TEAL, v -> {
            boolean[] on = new boolean[allScreens.size()]; for (int i = 0; i < on.length; i++) on[i] = screens.contains(allScreens.get(i));
            new AlertDialog.Builder(this).setTitle("الشاشات المسموحة").setMultiChoiceItems(allScreens.toArray(new String[0]), on, (d, i, c) -> on[i] = c)
                    .setPositiveButton("تم", (d, w) -> { screens.clear(); for (int i = 0; i < on.length; i++) if (on[i]) screens.add(allScreens.get(i)); })
                    .setNeutralButton("الافتراضي", (d, w) -> { screens.clear(); screens.addAll(Session.defaults()); for (String[] c : CAPS) screens.remove(c[0]); })
                    .setNegativeButton("إلغاء", null).show();
        }), 8);
        android.widget.ScrollView sv = new android.widget.ScrollView(this); sv.addView(box);
        new AlertDialog.Builder(this).setTitle(isNew ? "مستخدم جديد" : u.name).setView(sv).setPositiveButton("حفظ", (d, w) -> {
            String nm = name.getText().toString().trim(), un = user.getText().toString().trim(), pw = pass.getText().toString();
            if (nm.isEmpty() || un.isEmpty()) { Ux.alert(this, "أدخل الاسم واسم الدخول"); return; }
            for (User x : Store.db.users) if (x != u && x.username.equalsIgnoreCase(un)) { Ux.alert(this, "اسم الدخول مستخدم"); return; }
            if (isNew && pw.length() < 4) { Ux.alert(this, "كلمة المرور 4 أحرف على الأقل"); return; }
            boolean wasAdmin = !isNew && u.admin();
            if (wasAdmin && (!admin.isChecked() || !active.isChecked())) { int admins = 0; for (User x : Store.db.users) if (x.admin() && x.active) admins++;
                if (admins <= 1) { Ux.alert(this, "يجب أن يبقى مدير فعّال واحد على الأقل"); return; } }
            u.name = nm; u.username = un; if (!pw.isEmpty()) u.passHash = Session.hash(pw); u.role = admin.isChecked() ? "مدير" : "كاشير"; u.active = active.isChecked();
            Set<String> perms = new HashSet<>(screens);
            for (CheckBox x : caps) if (x.isChecked()) perms.add((String) x.getTag());
            u.perms = perms.isEmpty() ? "-" : String.join("\n", perms);
            if (isNew) { u.id = Store.db.next("user"); Store.db.users.add(u); }
            Store.save(); ad.set(Store.db.users);
        }).setNegativeButton("إلغاء", null).show();
    }
    EditText f(LinearLayout box, String label, String val) {
        Ux.add(box, Ux.text(this, label, 13, false, Ux.MUTED), 6); EditText e = Ux.field(this, label, false); e.setText(val); box.addView(e); return e;
    }
}
