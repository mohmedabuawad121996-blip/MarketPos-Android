package com.marketpro.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.market.Model;
import com.market.Model.InvLine;
import com.market.Model.InvType;
import com.market.Model.Invoice;
import com.market.Model.Party;
import com.market.Model.Product;
import com.market.Model.Wallet;
import com.market.Ops;
import com.market.Session;
import com.market.Store;
import com.market.Ui;

import java.util.ArrayList;
import java.util.List;

/** شاشة الفاتورة: بيع، شراء، مرتجعات، وطلبية — بالباركود (الكاميرا أو الكتابة) أو البحث بالاسم */
public class InvoiceActivity extends AppCompatActivity {
    InvType type; Invoice editing;
    final List<InvLine> lines = new ArrayList<>();
    long partyId, refId; long walletId;
    double invDiscount; long redeemPts; double redeemVal;
    Button partyBtn, heldB, ptsB; EditText code; TextView subT, discT, taxT, totalT; Ux.Adapter<InvLine> adapter;
    ActivityResultLauncher<ScanOptions> scanner;

    boolean buy() { return type == InvType.PURCHASE || type == InvType.PURCHASE_RETURN || type == InvType.ORDER; }
    boolean isReturn() { return type == InvType.SALE_RETURN || type == InvType.PURCHASE_RETURN; }
    int partyKind() { return type.customerSide() ? Model.CUSTOMER : Model.SUPPLIER; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        type = InvType.valueOf(getIntent().getStringExtra("type"));
        walletId = Store.db.defaultWallet();
        long editId = getIntent().getLongExtra("edit", 0);
        if (editId != 0) { editing = Store.db.invoice(editId);
            if (editing != null) { for (InvLine l : editing.lines) lines.add(l.copy()); partyId = editing.partyId; refId = editing.refId; invDiscount = Ui.r(editing.discount - editing.pointsValue, 3); walletId = editing.walletId;
                redeemPts = (long) editing.pointsUsed; redeemVal = editing.pointsValue; } }
        long from = getIntent().getLongExtra("returnFrom", 0);
        scanner = registerForActivityResult(new ScanContract(), r -> { if (r != null && r.getContents() != null) addCode(r.getContents()); });

        LinearLayout body = Ux.screen(this, (editing != null ? "تعديل " : "") + type.title + (editing != null ? " رقم " + editing.no : ""), false);
        // المورد / العميل والمحفظة
        LinearLayout top = Ux.row(this);
        partyBtn = Ux.button(this, "", Ux.TEAL, v -> pickParty()); top.addView(partyBtn, Ux.weight(1));
        if (isReturn()) { Button imp = Ux.button(this, "من فاتورة", Ux.PURPLE, v -> importFrom()); LinearLayout.LayoutParams lp = Ux.weight(0.6f); lp.setMarginStart(Ux.dp(this, 6)); top.addView(imp, lp); }
        if (type == InvType.SALE && com.market.Loyalty.enabled()) { ptsB = Ux.button(this, "النقاط", Ux.ORANGE, v -> redeem()); LinearLayout.LayoutParams lp = Ux.weight(0.6f); lp.setMarginStart(Ux.dp(this, 6)); top.addView(ptsB, lp); }
        body.addView(top);
        // الباركود
        LinearLayout scan = Ux.row(this);
        code = Ux.field(this, "الباركود أو اسم الصنف", false); code.setImeOptions(EditorInfo.IME_ACTION_DONE);
        code.setOnEditorActionListener((v, id, e) -> { if (id == EditorInfo.IME_ACTION_DONE || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN)) { submitCode(); return true; } return false; });
        scan.addView(code, Ux.weight(1));
        Button cam = Ux.button(this, "📷", Ux.BLUE, v -> scan()); LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ux.dp(this, 54), -2); cl.setMarginStart(Ux.dp(this, 6)); scan.addView(cam, cl);
        Button find = Ux.button(this, "🔍", Ux.PRIMARY, v -> search(code.getText().toString())); LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Ux.dp(this, 54), -2); fl.setMarginStart(Ux.dp(this, 6)); scan.addView(find, fl);
        Ux.add(body, scan, 8);
        // السطور
        ListView list = new ListView(this); list.setDivider(null);
        adapter = new Ux.Adapter<>(this::lineView); list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> editLine(adapter.getItem(i)));
        list.setOnItemLongClickListener((p, v, i, id) -> { InvLine l = adapter.getItem(i); Ux.confirm(this, "حذف " + l.name + " من الفاتورة؟", () -> { lines.remove(l); refresh(); }); return true; });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 0, 1); ll.topMargin = Ux.dp(this, 8); body.addView(list, ll);
        TextView hint = Ux.text(this, "اضغط على الصنف لتعديل الكمية أو السعر، واضغط مطولاً لحذفه", 12, false, Ux.MUTED); body.addView(hint);
        // المجاميع
        LinearLayout tot = Ux.card(this);
        subT = new TextView(this); discT = new TextView(this); taxT = new TextView(this); totalT = new TextView(this);
        tot.addView(subT); tot.addView(discT); tot.addView(taxT); tot.addView(totalT);
        discT.setOnClickListener(v -> Ux.input(this, "الخصم على الفاتورة", "0", Ui.q(invDiscount), true, s -> { invDiscount = Ui.num(s); refresh(); }));
        Ux.add(body, tot, 6);
        LinearLayout btns = Ux.row(this);
        Button save = Ux.button(this, type == InvType.ORDER ? "حفظ الطلبية" : type == InvType.QUOTE ? "حفظ عرض السعر" : "الدفع والحفظ", Ux.GREEN, v -> pay()); btns.addView(save, Ux.weight(2));
        Button clear = Ux.button(this, "جديدة", Ux.MUTED, v -> Ux.confirm(this, "مسح الفاتورة الحالية؟", this::reset)); LinearLayout.LayoutParams bl = Ux.weight(1); bl.setMarginStart(Ux.dp(this, 6)); btns.addView(clear, bl);
        if (type == InvType.SALE || type == InvType.PURCHASE) {
            heldB = Ux.button(this, "", Ux.ORANGE, v -> held()); LinearLayout.LayoutParams hl = Ux.weight(1.3f); hl.setMarginStart(Ux.dp(this, 6)); btns.addView(heldB, hl); }
        Ux.add(body, btns, 8);
        long quote = getIntent().getLongExtra("fromQuote", 0);
        if (quote != 0) { Invoice q = Store.db.invoice(quote); if (q != null) { for (InvLine l : q.lines) lines.add(l.copy()); partyId = q.partyId; invDiscount = q.discount; Ux.toast(this, "تم تحميل عرض السعر رقم " + q.no); } }
        if (from != 0) loadReturnFrom(Store.db.invoice(from));
        long order = getIntent().getLongExtra("fromOrder", 0);
        if (order != 0) { Invoice o = Store.db.invoice(order);
            if (o != null) { Invoice got = Ops.receivedBy(o, 0);
                if (got != null) Ux.alert(this, "هذه الطلبية استُلمت من قبل بفاتورة المشتريات رقم " + got.no);
                else { for (InvLine l : o.lines) lines.add(l.copy()); partyId = o.partyId; refId = o.id; Ux.toast(this, "تم تحميل أصناف الطلبية رقم " + o.no); } } }
        refresh();
    }

    // ------------------------------------------------ إضافة الأصناف
    void scan() {
        ScanOptions o = new ScanOptions(); o.setPrompt("وجّه الكاميرا إلى الباركود"); o.setBeepEnabled(true); o.setOrientationLocked(false);
        scanner.launch(o);
    }
    void submitCode() {
        String s = code.getText().toString().trim(); if (s.isEmpty()) return;
        if (Store.db.byBarcode(s) != null || com.market.Scale.looksLike(s)) { addCode(s); code.setText(""); } else search(s);
    }
    void addCode(String c) {
        Product p = Store.db.byBarcode(c.trim());
        if (p == null && com.market.Scale.looksLike(c.trim())) {
            com.market.Scale.Hit h = com.market.Scale.parse(c.trim());
            if (h != null) { InvLine l = new InvLine(); l.productId = h.p.id; l.name = h.p.name; l.barcode = h.p.barcode; l.unit = h.p.weighed ? "كغ" : h.p.unit; l.factor = 1;
                l.qty = h.qty; l.price = buy() ? h.p.cost : h.p.price; l.cost = h.p.cost; l.taxable = h.p.taxable; lines.add(l); refresh(); return; }
            Ux.alert(this, "باركود ميزان لصنف غير معرّف.\nرقم الصنف في الميزان: " + c.trim().substring(2, 2 + com.market.Scale.codeLen()) + "\nأدخله في خانة «رقم الميزان» للصنف من برنامج الكمبيوتر."); return;
        }
        if (p == null) { Ux.confirm(this, "الباركود " + c + " غير موجود. إضافة صنف جديد؟", () -> startActivity(new Intent(this, ProductsActivity.class).putExtra("newCode", c.trim()))); return; }
        add(p, Store.isBigCode(p, c));
    }
    void add(Product p, boolean big) {
        double f = big ? p.factor : 1;
        double price = buy() ? Ui.r(p.cost * f) : big ? (p.bigPrice > 0 ? p.bigPrice : Ui.r(p.price * f)) : p.price;
        for (InvLine l : lines) if (l.productId == p.id && l.factor == f && Math.abs(l.price - price) < 1e-9 && l.discount == 0) { l.qty += 1; refresh(); return; }
        InvLine l = new InvLine(); l.productId = p.id; l.name = p.name; l.barcode = p.barcode; l.unit = big ? p.bigUnit : p.unit; l.factor = f;
        l.qty = 1; l.price = price; l.cost = p.cost; l.taxable = p.taxable;
        lines.add(l); refresh();
    }
    void search(String q) {
        q = q.trim().toLowerCase();
        List<Product> found = new ArrayList<>();
        for (Product p : Store.db.products.values()) {
            if (!p.active) continue;
            if (q.isEmpty() || p.name.toLowerCase().contains(q) || (p.barcode != null && p.barcode.startsWith(q))) { found.add(p); if (found.size() >= 200) break; }
        }
        if (found.isEmpty()) { Ux.alert(this, "لا يوجد صنف بهذا الاسم أو الباركود"); return; }
        if (found.size() == 1) { add(found.get(0), false); code.setText(""); return; }
        List<String> names = new ArrayList<>();
        for (Product p : found) names.add(p.name + "   —   " + Ui.m(buy() ? p.cost : p.price) + "   (" + p.stockText() + ")");
        Ux.choose(this, "اختر الصنف", names, i -> { add(found.get(i), false); code.setText(""); });
    }

    View lineView(InvLine l, int pos) {
        String sub = Ui.q(l.qty) + " " + l.unit + " × " + Ui.m(l.price) + (l.discount > 0 ? "  - خصم " + Ui.m(l.discount) : "") + (l.offer != null && !l.offer.isEmpty() ? "  🏷 " + l.offer : "");
        return Ux.listRow(this, (pos + 1) + ". " + l.name, sub, Ui.m(l.total()), Ux.TEXT);
    }

    void editLine(InvLine l) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p = Ux.dp(this, 18); box.setPadding(p, Ux.dp(this, 8), p, 0);
        EditText qty = Ux.field(this, "الكمية", true); qty.setText(Ui.q(l.qty));
        EditText price = Ux.field(this, "السعر", true); price.setText(String.valueOf(l.price));
        EditText total = Ux.field(this, "المجموع (يُحسب منه السعر)", true);
        EditText disc = Ux.field(this, "الخصم على السطر", true); disc.setText(l.discount == 0 ? "" : String.valueOf(l.discount));
        box.addView(Ux.text(this, "الكمية (" + l.unit + ")", 13, false, Ux.MUTED)); box.addView(qty);
        box.addView(Ux.text(this, "السعر", 13, false, Ux.MUTED)); box.addView(price);
        box.addView(Ux.text(this, "أو اكتب المجموع", 13, false, Ux.MUTED)); box.addView(total);
        if (Session.cap("cap:discount")) { box.addView(Ux.text(this, "الخصم", 13, false, Ux.MUTED)); box.addView(disc); }
        new AlertDialog.Builder(this).setTitle(l.name).setView(box).setPositiveButton("حفظ", (d, w) -> {
            double q = Ui.num(qty.getText().toString()); if (q <= 0) { Ux.alert(this, "كمية غير صحيحة"); return; }
            double dc = Ui.num(disc.getText().toString());
            String t = total.getText().toString().trim();
            double pr = t.isEmpty() ? Ui.num(price.getText().toString()) : (Ui.num(t) + dc) / q; // المجموع يحدد السعر بدقة كما في الحاسوب
            if (pr < 0) { Ux.alert(this, "سعر غير صحيح"); return; }
            if (!buy() && !Session.cap("cap:price") && Math.abs(pr - l.price) > 1e-9) { Ux.alert(this, "ليس لديك صلاحية تغيير السعر"); return; }
            if (Math.abs(dc - l.discount) > 1e-9) l.manualDisc = true; l.qty = q; l.price = pr; l.discount = dc; refresh();
        }).setNegativeButton("إلغاء", null).setNeutralButton("حذف", (d, w) -> { lines.remove(l); refresh(); }).show();
    }

    // ------------------------------------------------ الطرف والمرتجع
    void pickParty() {
        List<Party> ps = Store.db.parties(partyKind());
        List<String> names = new ArrayList<>(); names.add(type.customerSide() ? "— عميل نقدي —" : "— مورد نقدي —"); names.add("+ إضافة جديد");
        for (Party p : ps) names.add(p.name + (p.phone.isEmpty() ? "" : "  (" + p.phone + ")"));
        Ux.choose(this, type.customerSide() ? "العميل" : "المورد", names, i -> {
            if (i == 0) partyId = 0;
            else if (i == 1) { Ux.input(this, "الاسم", "الاسم", "", false, s -> { if (s.isEmpty()) return;
                Party p = new Party(); p.id = Store.db.next("party"); p.kind = partyKind(); p.name = s; Store.db.parties.put(p.id, p); Store.save(); partyId = p.id; refresh(); }); return; }
            else partyId = ps.get(i - 2).id;
            redeemPts = 0; redeemVal = 0;
            refresh();
        });
    }
    void importFrom() {
        InvType ot = type == InvType.SALE_RETURN ? InvType.SALE : InvType.PURCHASE;
        Ux.input(this, "رقم " + ot.title + " الأصلية", "رقم الفاتورة", "", true, s -> {
            Invoice o = Store.db.invoiceByNo(ot, (long) Ui.num(s));
            if (o == null || o.cancelled) { Ux.alert(this, "لا توجد فاتورة فعّالة بهذا الرقم"); return; }
            loadReturnFrom(o);
        });
    }
    void loadReturnFrom(Invoice o) {
        if (o == null) return;
        List<InvLine> rem = Ops.returnableLines(o);
        if (rem.isEmpty()) { Ux.alert(this, "كل أصناف هذه الفاتورة أُرجعت من قبل"); return; }
        lines.clear(); lines.addAll(rem); refId = o.id; partyId = o.partyId; refresh();
        Ux.toast(this, "تم تحميل المتبقي للإرجاع من الفاتورة رقم " + o.no);
    }

    // ------------------------------------------------ المجاميع والحفظ
    Invoice build() {
        Invoice inv = new Invoice(); inv.type = type; inv.partyId = partyId; inv.partyName = partyId == 0 ? "" : Store.db.partyName(partyId);
        inv.refId = refId; inv.discount = Ui.r(invDiscount + redeemVal, 3); inv.pointsUsed = redeemPts; inv.pointsValue = redeemVal; inv.walletId = walletId; inv.user = Session.name();
        for (InvLine l : lines) inv.lines.add(l.copy());
        if (editing != null) inv.date = editing.date;
        Ops.compute(inv); return inv;
    }
    void refresh() {
        partyBtn.setText((type.customerSide() ? "العميل: " : "المورد: ") + (partyId == 0 ? "نقدي" : Store.db.partyName(partyId)));
        if (type == InvType.SALE || type == InvType.QUOTE) com.market.Offers.apply(lines); // العروض التلقائية
        adapter.set(lines);
        Invoice inv = build();
        setPair(subT, "المجموع", Ui.m(inv.subtotal), Ux.TEXT);
        setPair(discT, "الخصم (اضغط للتعديل)", Ui.m(invDiscount), Ux.ORANGE);
        setPair(taxT, "الضريبة", Ui.m(inv.tax), Ux.MUTED);
        setPair(totalT, "الإجمالي", Ui.m(inv.total) + " " + Store.db.get("currency", "د.أ"), Ux.PRIMARY);
        totalT.setTextSize(20);
        if (ptsB != null) ptsB.setText(redeemPts > 0 ? "نقاط: " + redeemPts : partyId == 0 ? "النقاط" : "النقاط (" + com.market.Loyalty.balance(partyId) + ")");
        if (heldB != null) heldB.setText("تعليق / المعلّقة (" + heldCount() + ")");
    }
    /** استبدال نقاط العميل كخصم */
    void redeem() {
        if (partyId == 0) { Ux.alert(this, "اختر العميل أولاً"); return; }
        if (redeemPts > 0) { Ux.confirm(this, "إلغاء استبدال " + redeemPts + " نقطة؟", () -> { redeemPts = 0; redeemVal = 0; refresh(); }); return; }
        long bal = com.market.Loyalty.balance(partyId);
        if (bal < com.market.Loyalty.minRedeem()) { Ux.alert(this, "رصيد العميل: " + bal + " نقطة\nأقل عدد للاستبدال: " + com.market.Loyalty.minRedeem()); return; }
        if (com.market.Loyalty.value() <= 0) { Ux.alert(this, "حدد قيمة النقطة من الإعدادات"); return; }
        Invoice inv = build();
        double room = Ops.noLoss() ? Ops.maxInvoiceDiscount(inv) - inv.discount : inv.subtotal - inv.discount;
        long maxP = Math.min(bal, (long) Math.floor(Math.max(0, room) / com.market.Loyalty.value() + 1e-9));
        if (maxP < com.market.Loyalty.minRedeem()) { Ux.alert(this, "قيمة الفاتورة لا تسمح باستبدال النقاط"); return; }
        Ux.input(this, "رصيد العميل " + bal + " نقطة (النقطة = " + Ui.m(com.market.Loyalty.value()) + ")\nأقصى استبدال: " + maxP + " نقطة = " + Ui.m(maxP * com.market.Loyalty.value()), "عدد النقاط", String.valueOf(maxP), true, x -> {
            long n = (long) Ui.num(x);
            if (n < com.market.Loyalty.minRedeem() || n > maxP) { Ux.alert(this, "أدخل عدداً بين " + com.market.Loyalty.minRedeem() + " و " + maxP); return; }
            redeemPts = n; redeemVal = Ui.r(n * com.market.Loyalty.value(), 3); refresh(); });
    }
    int heldCount() { int n = 0; for (Invoice h : Store.db.held) if (h.type == type) n++; return n; }
    /** تعليق الفاتورة الحالية أو استرجاع فاتورة معلّقة */
    void held() {
        List<Invoice> hs = new ArrayList<>(); for (Invoice h : Store.db.held) if (h.type == type) hs.add(h);
        List<String> o = new ArrayList<>(); if (!lines.isEmpty() && editing == null) o.add("⏸ تعليق الفاتورة الحالية");
        for (Invoice h : hs) o.add("▶ " + Ui.dt(h.date) + "  •  " + (h.partyId == 0 ? "نقدي" : Store.db.partyName(h.partyId)) + "  •  " + h.lines.size() + " صنف  •  " + Ui.m(h.total));
        if (o.isEmpty()) { Ux.alert(this, "لا توجد فواتير معلّقة"); return; }
        int off = (!lines.isEmpty() && editing == null) ? 1 : 0;
        Ux.choose(this, "الفواتير المعلّقة", o, i -> {
            if (i < off) { Invoice h = build(); h.id = Store.db.next("held"); h.date = java.time.LocalDateTime.now(); h.partyName = Store.db.partyName(h.partyId);
                Store.db.held.add(h); Store.save(); reset(); Ux.toast(this, "تم تعليق الفاتورة"); return; }
            Invoice h = hs.get(i - off);
            Runnable load = () -> { lines.clear(); for (InvLine l : h.lines) lines.add(l.copy()); partyId = h.partyId; invDiscount = h.discount; refId = h.refId;
                Store.db.held.remove(h); Store.save(); refresh(); };
            if (!lines.isEmpty()) Ux.confirm(this, "الفاتورة الحالية غير محفوظة وستُستبدل. متابعة؟", load); else load.run();
        });
    }
    void setPair(TextView t, String label, String value, int color) {
        t.setText(label + ":   " + value); t.setTextColor(color); t.setTextSize(15); t.setPadding(0, Ux.dp(this, 3), 0, Ux.dp(this, 3));
    }
    void reset() { lines.clear(); partyId = 0; refId = 0; invDiscount = 0; redeemPts = 0; redeemVal = 0; editing = null; refresh(); }

    void pay() {
        if (lines.isEmpty()) { Ux.alert(this, "الفاتورة فارغة"); return; }
        Invoice inv = build();
        if (type.noEffect()) { inv.paid = 0; finishSave(inv); return; }
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p = Ux.dp(this, 18); box.setPadding(p, Ux.dp(this, 8), p, 0);
        TextView big = Ux.text(this, Ui.m(inv.total), 32, true, Ux.PRIMARY); big.setGravity(android.view.Gravity.CENTER); box.addView(big);
        box.addView(Ux.text(this, "المبلغ المدفوع", 13, false, Ux.MUTED));
        EditText paid = Ux.field(this, "المدفوع", true); paid.setText(Ui.q(inv.total)); box.addView(paid);
        TextView change = Ux.text(this, "", 15, true, Ux.BLUE); box.addView(change);
        final EditText[] tend = new EditText[1];
        if (type == InvType.SALE) {
            box.addView(Ux.text(this, "المبلغ المستلم من الزبون (لحساب الباقي)", 13, false, Ux.MUTED));
            EditText tendered = Ux.field(this, "المستلم", true); box.addView(tendered); tend[0] = tendered;
            tendered.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) {}
                public void afterTextChanged(Editable e) { double t = Ui.num(e.toString()); change.setText(t > 0 ? "الباقي للزبون: " + Ui.m(Math.max(0, t - Ui.num(paid.getText().toString()))) : ""); }
            });
        }
        List<Wallet> ws = Store.db.activeWallets();
        Button wb = Ux.button(this, "الصندوق: " + Store.db.walletName(walletId), Ux.TEAL, null); Ux.add(box, wb, 8);
        wb.setOnClickListener(v -> { List<String> n = new ArrayList<>(); for (Wallet w : ws) n.add(w.name); Ux.choose(this, "طريقة الدفع", n, i -> { walletId = ws.get(i).id; wb.setText("الصندوق: " + ws.get(i).name); }); });
        new AlertDialog.Builder(this).setTitle(type.title).setView(box)
                .setPositiveButton("حفظ", (d, w) -> { Invoice x = build(); x.paid = Ui.r(Ui.num(paid.getText().toString())); if (tend[0] != null) x.tendered = Ui.r(Ui.num(tend[0].getText().toString())); finishSave(x); })
                .setNeutralButton("آجل (بدون دفع)", (d, w) -> { Invoice x = build(); x.paid = 0; finishSave(x); })
                .setNegativeButton("رجوع", null).show();
    }

    void finishSave(Invoice inv) {
        final Invoice[] out = new Invoice[1];
        boolean ok = Ux.run(this, () -> out[0] = editing != null ? Ops.replace(editing, inv) : Ops.save(inv));
        if (!ok) return;
        Invoice s = out[0];
        if (com.market.JoFotara.enabled() && com.market.JoFotara.auto() && com.market.JoFotara.applies(s)) {
            android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, "الفوترة الوطنية", "جاري إرسال الفاتورة رقم " + s.no + "…", true);
            new Thread(() -> { String r = com.market.JoFotara.submit(s);
                runOnUiThread(() -> { try { pd.dismiss(); } catch (Exception ignored) {}
                    if (!com.market.JoFotara.submitted(s)) Ux.alert(this, "لم تُقبل الفاتورة في نظام الفوترة الوطني:\n" + r + "\n\nالفاتورة محفوظة، ويمكن إعادة إرسالها من الإعدادات ← الفوترة الوطنية");
                    saved(s); }); }).start();
        } else saved(s);
    }
    void saved(Invoice s) {
        if (Printer.auto() && !Printer.mac().isEmpty() && !s.type.noEffect()) Printer.printInvoice(this, s);
        List<String> o = java.util.Arrays.asList("طباعة على الطابعة (بلوتوث)", "فاتورة PDF (طباعة / مشاركة)", "إرسال واتساب للعميل", "مشاركة كنص");
        new AlertDialog.Builder(this).setTitle("تم الحفظ")
                .setMessage(s.type.title + " رقم " + s.no + "\nالإجمالي: " + Ui.m(s.total) + (s.tendered > s.paid + 0.0005 ? "\nالباقي للزبون: " + Ui.m(s.tendered - s.paid) : "")
                        + (s.total - s.paid > 0.0005 && !s.type.noEffect() ? "\nآجل: " + Ui.m(s.total - s.paid) : ""))
                .setPositiveButton("فاتورة جديدة", (d, w) -> { if (editing != null) finish(); else reset(); })
                .setNeutralButton("طباعة / مشاركة", (d, w) -> { Ux.choose(this, "الفاتورة", o, k -> {
                    if (k == 0) Printer.printInvoice(this, s); else if (k == 1) Pdf.invoice(this, s);
                    else if (k == 2) { com.market.Model.Party p = s.partyId == 0 ? null : Store.db.party(s.partyId); WA.send(this, p == null ? "" : p.phone, com.market.WhatsApp.invoiceText(s)); }
                    else Share.invoice(this, s); }); if (editing != null) finish(); else reset(); })
                .setCancelable(false).show();
    }
}
