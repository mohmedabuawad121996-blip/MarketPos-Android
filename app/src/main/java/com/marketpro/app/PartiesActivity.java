package com.marketpro.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.market.Model;
import com.market.Model.Party;
import com.market.Model.Wallet;
import com.market.Ops;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** العملاء أو الموردون: الأرصدة، الإضافة والتعديل، سندات القبض والصرف، كشف الحساب */
public class PartiesActivity extends Activity {
    int kind; EditText search; TextView sum; Ux.Adapter<Party> adapter; Map<Long, Double> bal;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        kind = getIntent().getIntExtra("kind", Model.CUSTOMER);
        LinearLayout body = Ux.screen(this, kind == Model.CUSTOMER ? "العملاء" : "الموردون", false);
        search = Ux.field(this, "بحث بالاسم أو الهاتف", false); body.addView(search);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable e) { load(); } });
        Ux.add(body, Ux.button(this, kind == Model.CUSTOMER ? "+ عميل جديد" : "+ مورد جديد", Ux.PRIMARY, v -> edit(null)), 6);
        sum = Ux.text(this, "", 13, true, Ux.PRIMARY); Ux.add(body, sum, 6);
        ListView list = new ListView(this); list.setDivider(null);
        adapter = new Ux.Adapter<>(this::row); list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> actions(adapter.getItem(i)));
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        String d = getIntent().getStringExtra("do"); if (d == null) d = "";
        if (d.equals("add")) edit(null);
        else if (d.equals("opening") || d.equals("voucher")) { boolean op = d.equals("opening");
            List<Party> ps = Store.db.parties(kind); List<String> n = new ArrayList<>(); for (Party p : ps) n.add(p.name);
            if (ps.isEmpty()) Ux.alert(this, "لا يوجد " + (kind == Model.CUSTOMER ? "عملاء" : "موردون") + " بعد");
            else Ux.choose(this, op ? "الرصيد الافتتاحي لـ" : (kind == Model.CUSTOMER ? "قبض من" : "صرف إلى"), n, i -> { Party p = ps.get(i);
                if (op) Ux.input(this, "الرصيد الافتتاحي لـ " + p.name + (kind == Model.CUSTOMER ? " (موجب = عليه لنا)" : " (موجب = له علينا)"), "المبلغ", Ui.q(p.opening), true,
                        s -> { if (Ux.run(this, () -> Ops.setOpening(p, Ui.num(s)))) load(); });
                else voucher(p); }); }
    }
    @Override protected void onResume() { super.onResume(); load(); }

    void load() {
        bal = Ops.allBalances(kind);
        String q = search.getText().toString().trim().toLowerCase();
        List<Party> l = new ArrayList<>(); double tot = 0;
        for (Party p : Store.db.parties(kind)) {
            tot += bal.getOrDefault(p.id, 0.0);
            if (!q.isEmpty() && !p.name.toLowerCase().contains(q) && !(p.phone != null && p.phone.contains(q))) continue;
            l.add(p);
        }
        adapter.set(l);
        sum.setText("العدد: " + l.size() + "  |  مجموع الأرصدة " + (kind == Model.CUSTOMER ? "(لنا): " : "(علينا): ") + Ui.m(tot));
    }

    View row(Party p, int pos) {
        double v = bal.getOrDefault(p.id, 0.0);
        return Ux.listRow(this, p.name, p.phone, Ui.m(v), v > 0 ? Ux.RED : Ux.PRIMARY);
    }

    void actions(Party p) {
        List<String> a = new ArrayList<>();
        a.add(kind == Model.CUSTOMER ? "سند قبض (استلام دفعة)" : "سند صرف (دفع للمورد)");
        a.add("كشف الحساب"); a.add("تعديل البيانات"); a.add("إرسال الرصيد واتساب");
        Ux.choose(this, p.name + " — الرصيد " + Ui.m(bal.getOrDefault(p.id, 0.0)), a, i -> {
            if (i == 0) voucher(p);
            else if (i == 1) startActivity(new Intent(this, StatementActivity.class).putExtra("kind", kind).putExtra("id", p.id));
            else if (i == 2) edit(p);
            else WA.send(this, p.phone, com.market.WhatsApp.balanceText(p));
        });
    }

    void voucher(Party p) {
        boolean in = kind == Model.CUSTOMER;
        Ux.input(this, (in ? "قبض من " : "صرف إلى ") + p.name, "المبلغ", "", true, s -> {
            double amt = Ui.num(s);
            pickWallet(this, w -> { if (Ux.run(this, () -> Ops.voucher(in, kind, p.id, amt, w, 0, "", LocalDateTime.now()))) { Ux.toast(this, "تم حفظ السند"); load(); } });
        });
    }

    interface WalletCb { void got(long id); }
    static void pickWallet(Activity a, WalletCb cb) {
        List<Wallet> ws = Store.db.activeWallets();
        if (ws.size() <= 1) { cb.got(ws.isEmpty() ? 0 : ws.get(0).id); return; }
        List<String> n = new ArrayList<>(); for (Wallet w : ws) n.add(w.name + "  (" + Ui.m(Ops.walletBalance(w.id)) + ")");
        Ux.choose(a, "اختر الصندوق", n, i -> cb.got(ws.get(i).id));
    }

    void edit(Party old) {
        boolean isNew = old == null;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pd = Ux.dp(this, 18); box.setPadding(pd, Ux.dp(this, 8), pd, 0);
        EditText name = f(box, "الاسم", false, isNew ? "" : old.name), phone = f(box, "الهاتف", false, isNew ? "" : old.phone),
                addr = f(box, "العنوان", false, isNew ? "" : old.address), limit = f(box, "حد الائتمان (0 = بلا حد)", true, isNew || old.creditLimit == 0 ? "" : Ui.q(old.creditLimit));
        EditText open = f(box, "الرصيد الافتتاحي", true, isNew || old.opening == 0 ? "" : Ui.q(old.opening));
        ScrollView sv = new ScrollView(this); sv.addView(box);
        new AlertDialog.Builder(this).setTitle(isNew ? "إضافة" : "تعديل " + old.name).setView(sv).setPositiveButton("حفظ", (d, w) -> {
            String nm = name.getText().toString().trim(); if (nm.isEmpty()) { Ux.alert(this, "الاسم مطلوب"); return; }
            Ux.run(this, () -> {
                Party p = isNew ? new Party() : old;
                if (isNew) { p.id = Store.db.next("party"); p.kind = kind; }
                p.name = nm; p.phone = phone.getText().toString().trim(); p.address = addr.getText().toString().trim();
                p.creditLimit = Ui.num(limit.getText().toString());
                if (isNew) Store.db.parties.put(p.id, p);
                Ops.setOpening(p, Ui.num(open.getText().toString()));
            });
            load();
        }).setNegativeButton("إلغاء", null).show();
    }
    EditText f(LinearLayout box, String label, boolean num, String val) {
        box.addView(Ux.text(this, label, 13, false, Ux.MUTED));
        EditText e = Ux.field(this, label, num); if (val != null) e.setText(val); box.addView(e); return e;
    }
}
