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
import android.widget.TextView;

import com.market.Model.InvLine;
import com.market.Model.InvType;
import com.market.Model.Invoice;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** سجل الفواتير: بحث وفترة، وتفاصيل الفاتورة مع المشاركة والتعديل والإرجاع والاستلام والإلغاء */
public class InvoicesActivity extends Activity {
    InvType type; int range = 1; // 0 اليوم، 1 هذا الشهر، 2 الكل
    EditText search; TextView sum; Ux.Adapter<Invoice> adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        type = InvType.valueOf(getIntent().getStringExtra("type"));
        LinearLayout body = Ux.screen(this, "سجل " + type.title, false);
        search = Ux.field(this, "بحث برقم الفاتورة أو الاسم", false); body.addView(search);
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {} public void afterTextChanged(Editable e) { load(); } });
        LinearLayout chips = Ux.row(this); String[] names = {"اليوم", "هذا الشهر", "الكل"};
        for (int i = 0; i < 3; i++) { final int k = i; android.widget.Button c = Ux.button(this, names[i], Ux.TEAL, v -> { range = k; load(); });
            LinearLayout.LayoutParams lp = Ux.weight(1); if (i > 0) lp.setMarginStart(Ux.dp(this, 6)); chips.addView(c, lp); }
        Ux.add(body, chips, 6);
        sum = Ux.text(this, "", 14, true, Ux.PRIMARY); Ux.add(body, sum, 6);
        ListView list = new ListView(this); list.setDivider(null);
        adapter = new Ux.Adapter<>(this::row); list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> details(adapter.getItem(i)));
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    @Override protected void onResume() { super.onResume(); load(); }

    void load() {
        String q = search.getText().toString().trim();
        LocalDate today = LocalDate.now(), from = range == 0 ? today : range == 1 ? today.withDayOfMonth(1) : LocalDate.of(1900, 1, 1);
        List<Invoice> l = new ArrayList<>(); double total = 0; int n = 0;
        for (Invoice i : Store.db.invoices) {
            if (i.type != type || i.date.toLocalDate().isBefore(from)) continue;
            if (!q.isEmpty() && !String.valueOf(i.no).equals(q) && !Store.db.partyName(i.partyId).contains(q)) continue;
            l.add(i); if (!i.cancelled) { total += i.total; n++; }
        }
        Collections.reverse(l);
        adapter.set(l);
        sum.setText("عدد الفواتير: " + n + "   |   الإجمالي: " + Ui.m(total));
    }

    View row(Invoice i, int pos) {
        String st = i.cancelled ? "ملغاة" : type == InvType.ORDER ? (Ops.receivedBy(i, 0) != null ? "مُستلمة" : "بانتظار الاستلام")
                : i.total - i.paid > 0.0005 ? "آجل" : "نقدي";
        return Ux.listRow(this, "#" + i.no + "  " + (i.partyId == 0 ? "نقدي" : Store.db.partyName(i.partyId)), Ui.dt(i.date) + "  •  " + st,
                Ui.m(i.total), i.cancelled ? Ux.MUTED : Ux.PRIMARY);
    }

    void details(Invoice inv) { details(this, inv, this::load); }

    /** تفاصيل الفاتورة مع الإجراءات (تُستخدم أيضاً من التقارير) */
    public static void details(Activity a, Invoice inv, Runnable after) {
        InvType type = inv.type;
        StringBuilder b = new StringBuilder();
        for (InvLine l : inv.lines) b.append("• ").append(l.name).append("\n    ").append(Ui.q(l.qty)).append(' ').append(l.unit).append(" × ").append(Ui.m(l.price))
                .append(l.discount > 0 ? " - " + Ui.m(l.discount) : "").append(" = ").append(Ui.m(l.total())).append('\n');
        b.append("\nالمجموع: ").append(Ui.m(inv.subtotal));
        if (inv.discount > 0) b.append("\nالخصم: ").append(Ui.m(inv.discount));
        if (inv.tax > 0) b.append("\nالضريبة: ").append(Ui.m(inv.tax));
        b.append("\nالإجمالي: ").append(Ui.m(inv.total)).append("\nالمدفوع: ").append(Ui.m(inv.paid));
        if (inv.cancelled) b.append("\n\n(ملغاة)");
        if (com.market.JoFotara.enabled() && com.market.JoFotara.applies(inv)) b.append("\nالفوترة الوطنية: ").append(com.market.JoFotara.statusText(inv)).append(inv.einvMsg == null || inv.einvMsg.isEmpty() ? "" : "\n" + inv.einvMsg);
        List<String> acts = new ArrayList<>(); List<Runnable> run = new ArrayList<>();
        acts.add("مشاركة كنص"); run.add(() -> Share.invoice(a, inv));
        acts.add("فاتورة PDF (طباعة / مشاركة)"); run.add(() -> Pdf.invoice(a, inv));
        acts.add("طباعة على الطابعة (بلوتوث)"); run.add(() -> Printer.printInvoice(a, inv));
        acts.add("إرسال واتساب"); run.add(() -> { com.market.Model.Party p = inv.partyId == 0 ? null : Store.db.party(inv.partyId); WA.send(a, p == null ? "" : p.phone, com.market.WhatsApp.invoiceText(inv)); });
        if (com.market.JoFotara.enabled() && com.market.JoFotara.applies(inv) && !com.market.JoFotara.submitted(inv) && !inv.cancelled) { acts.add("إرسال للفوترة الوطنية");
            run.add(() -> { android.app.ProgressDialog pd = android.app.ProgressDialog.show(a, null, "جاري الإرسال…", true);
                new Thread(() -> { String r = com.market.JoFotara.submit(inv); a.runOnUiThread(() -> { try { pd.dismiss(); } catch (Exception ignored) {} Ux.alert(a, r); if (after != null) after.run(); }); }).start(); }); }
        if (!inv.cancelled) {
            if ((Session.cap("cap:cancel") || Session.admin()) && !com.market.JoFotara.submitted(inv)) { acts.add("تعديل"); run.add(() -> a.startActivity(new Intent(a, InvoiceActivity.class).putExtra("type", type.name()).putExtra("edit", inv.id))); }
            if (type == InvType.SALE || type == InvType.PURCHASE) { acts.add("إرجاع من هذه الفاتورة"); InvType rt = type == InvType.SALE ? InvType.SALE_RETURN : InvType.PURCHASE_RETURN;
                run.add(() -> a.startActivity(new Intent(a, InvoiceActivity.class).putExtra("type", rt.name()).putExtra("returnFrom", inv.id))); }
            if (type == InvType.QUOTE) { acts.add("تحويل إلى فاتورة مبيعات");
                run.add(() -> a.startActivity(new Intent(a, InvoiceActivity.class).putExtra("type", InvType.SALE.name()).putExtra("fromQuote", inv.id))); }
            if (type == InvType.ORDER && Ops.receivedBy(inv, 0) == null) { acts.add("استلام الطلبية (فاتورة مشتريات)");
                run.add(() -> a.startActivity(new Intent(a, InvoiceActivity.class).putExtra("type", InvType.PURCHASE.name()).putExtra("fromOrder", inv.id))); }
            if (Session.cap("cap:cancel") || Session.admin()) { acts.add("إلغاء الفاتورة"); run.add(() -> Ux.confirm(a, "إلغاء " + type.title + " رقم " + inv.no + "؟"
                    + (type.noEffect() ? "" : "\nسيُعكس أثرها على المخزون والصندوق والحساب."), () -> { if (Ux.run(a, () -> Ops.cancel(inv)) && after != null) after.run(); })); }
        }
        new Dlg(a).setTitle(type.title + " رقم " + inv.no + " — " + Ui.dt(inv.date)).setMessage(b.toString())
                .setPositiveButton("إغلاق", null)
                .setNeutralButton("إجراءات", (d, w) -> Ux.choose(a, "إجراءات", acts, i -> run.get(i).run())).show();
    }
}
