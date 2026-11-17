package com.marketpro.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.market.Model.Category;
import com.market.Model.ExpenseCat;
import com.market.Model.Product;
import com.market.Model.Wallet;
import com.market.Model.Warehouse;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** قوائم بسيطة: التصنيفات، بنود المصروفات، المحافظ، المستودعات */
public class ListsActivity extends Activity {
    String mode; Ux.Adapter<Object> ad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getStringExtra("mode");
        String title = mode.equals("cat") ? "التصنيفات" : mode.equals("expcat") ? "بنود المصروفات" : mode.equals("wallet") ? "إدارة المحافظ" : "المستودعات";
        LinearLayout body = Ux.screen(this, title, false);
        body.addView(Ux.button(this, "+ إضافة", Ux.GREEN, v -> add()));
        ListView list = new ListView(this); list.setDivider(null);
        ad = new Ux.Adapter<>(this::row); list.setAdapter(ad);
        list.setOnItemClickListener((p, v, i, id) -> actions(ad.getItem(i)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1); lp.topMargin = Ux.dp(this, 8); body.addView(list, lp);
        load();
    }
    void load() {
        List<Object> l = new ArrayList<>();
        if (mode.equals("cat")) l.addAll(Store.db.categories); else if (mode.equals("expcat")) l.addAll(Store.db.expenseCats);
        else if (mode.equals("wallet")) l.addAll(Store.db.wallets); else l.addAll(Store.db.warehouses);
        ad.set(l);
    }
    View row(Object o, int pos) {
        if (o instanceof Category) { int n = 0; for (Product p : Store.db.products.values()) if (p.active && p.categoryId == ((Category) o).id) n++; return Ux.listRow(this, o.toString(), n + " صنف", null, 0); }
        if (o instanceof Wallet) { Wallet w = (Wallet) o; return Ux.listRow(this, w.name, w.type + (w.active ? "" : "  •  موقوفة"), Ui.m(Ops.walletBalance(w.id)), Ux.PRIMARY); }
        if (o instanceof Warehouse) { Warehouse w = (Warehouse) o; return Ux.listRow(this, w.name, w.active ? "فعّال" : "موقوف", null, 0); }
        return Ux.listRow(this, o.toString(), null, null, 0);
    }
    void add() {
        Ux.input(this, "الاسم", "الاسم", "", false, s -> { if (s.isEmpty()) return;
            if (mode.equals("cat")) { Category c = new Category(); c.id = Store.db.next("cat"); c.name = s; Store.db.categories.add(c); }
            else if (mode.equals("expcat")) { ExpenseCat c = new ExpenseCat(); c.id = Store.db.next("expcat"); c.name = s; Store.db.expenseCats.add(c); }
            else if (mode.equals("wallet")) { Wallet w = new Wallet(); w.id = Store.db.next("wallet"); w.name = s;
                Ux.choose(this, "نوع المحفظة", Arrays.asList("نقدي", "بنك", "بطاقة", "محفظة إلكترونية", "شيكات"), i -> { w.type = Arrays.asList("نقدي", "بنك", "بطاقة", "محفظة إلكترونية", "شيكات").get(i); Store.save(); load(); });
                Store.db.wallets.add(w); }
            else { Warehouse w = new Warehouse(); w.id = Store.db.next("warehouse"); w.name = s; Store.db.warehouses.add(w); }
            Store.save(); load(); });
    }
    void actions(Object o) {
        List<String> a = new ArrayList<>(Arrays.asList("إعادة التسمية"));
        boolean toggle = o instanceof Wallet || o instanceof Warehouse; if (toggle) a.add("إيقاف / تفعيل"); else a.add("حذف");
        Ux.choose(this, o.toString(), a, i -> {
            if (i == 0) Ux.input(this, "الاسم الجديد", "الاسم", o.toString(), false, s -> { if (s.isEmpty()) return;
                if (o instanceof Category) ((Category) o).name = s; else if (o instanceof ExpenseCat) ((ExpenseCat) o).name = s;
                else if (o instanceof Wallet) ((Wallet) o).name = s; else ((Warehouse) o).name = s; Store.save(); load(); });
            else if (o instanceof Wallet) { Wallet w = (Wallet) o;
                if (w.active && w.id == Store.db.defaultWallet()) { Ux.alert(this, "لا يمكن إيقاف الصندوق الرئيسي"); return; }
                w.active = !w.active; Store.save(); load(); }
            else if (o instanceof Warehouse) { Warehouse w = (Warehouse) o; if (w.active && Store.db.activeWarehouses().size() <= 1) { Ux.alert(this, "يجب أن يبقى مستودع واحد على الأقل"); return; }
                w.active = !w.active; Store.save(); load(); }
            else if (o instanceof Category) { Category c = (Category) o; for (Product p : Store.db.products.values()) if (p.categoryId == c.id && p.active) { Ux.alert(this, "التصنيف مستخدم في أصناف. انقلها أولاً"); return; }
                Ux.confirm(this, "حذف التصنيف " + c.name + "؟", () -> { Store.db.categories.remove(c); Store.save(); load(); }); }
            else { ExpenseCat c = (ExpenseCat) o; for (com.market.Model.Expense e : Store.db.expenses) if (e.catId == c.id) { Ux.alert(this, "البند مستخدم في مصروفات مسجلة"); return; }
                Ux.confirm(this, "حذف البند " + c.name + "؟", () -> { Store.db.expenseCats.remove(c); Store.save(); load(); }); }
        });
    }
}
