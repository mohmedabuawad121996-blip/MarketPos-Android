package com.market;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** نماذج البيانات */
public final class Model {
    private Model() {}

    public static final int CUSTOMER = 0, SUPPLIER = 1;

    /** مستخدم البرنامج */
    public static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public String name = "", username = "", passHash = "", role = "مدير"; public boolean active = true;
        public LocalDateTime lastLogin;
        public String photo = ""; // صورة المستخدم PNG بصيغة Base64
        public String perms = "";  // صلاحيات مخصصة (سطر لكل صلاحية). فارغ = الافتراضي للكاشير
        public boolean admin() { return "مدير".equals(role); }
        public String toString() { return name; }
    }

    public static class Category implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public String name;
        public String toString() { return name; }
    }

    public static class Product implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id;
        public String barcode = "", name = "";
        public long categoryId;
        public String unit = "حبة";      // الوحدة الصغرى
        public String bigUnit = "";      // الوحدة الكبرى (كرتونة/باكيت)
        public double factor = 1;        // عدد الحبات في الوحدة الكبرى
        public double cost;              // تكلفة الحبة
        public double price;             // سعر بيع الحبة
        public double bigPrice;          // سعر بيع الوحدة الكبرى
        public String bigBarcode = "";   // باركود الوحدة الكبرى (مسحه يبيع الوحدة الكبرى)
        public String extraBarcodes = ""; // باركودات إضافية للوحدة الصغرى مفصولة بفاصلة
        public java.util.List<String> extraCodes() {
            java.util.List<String> l = new java.util.ArrayList<>();
            if (extraBarcodes != null) for (String s : extraBarcodes.split("[,،;\\s]+")) if (!s.trim().isEmpty()) l.add(s.trim());
            return l;
        }
        public double qty;               // الكمية بالحبة
        public double minQty;
        public String plu = "";          // رقم الصنف في الميزان (للباركود الذي يبدأ بـ 2)
        public boolean weighed;          // يُباع بالوزن (كغ)
        public boolean taxable = true;
        public boolean active = true;
        public String toString() { return name; }
        public boolean hasBig() { return bigUnit != null && !bigUnit.trim().isEmpty() && factor > 1; }
        public String bigCode() { return bigBarcode == null ? "" : bigBarcode.trim(); }
        /** الرصيد بالشكل «2 باكيت + 5 حبة» */
        public String stockText() {
            if (!hasBig()) return Ui.q(qty) + " " + unit;
            double a = Math.abs(qty); long big = (long) Math.floor(a / factor + 1e-9); double rest = Ui.r(a - big * factor, 3);
            String s = (qty < 0 ? "-" : "") + (big > 0 ? big + " " + bigUnit : "") + (big > 0 && rest > 0 ? " + " : "") + (rest > 0 || big == 0 ? Ui.q(rest) + " " + unit : "");
            return s.trim();
        }
    }

    public static class Party implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public int kind;
        public String name = "", phone = "", address = "", notes = "";
        public String taxId = "", idType = "NIN"; // للفوترة الوطنية: الرقم الوطني NIN / الضريبي TN / جواز PN
        public double creditLimit; public double opening;
        public boolean active = true;
        public LocalDateTime created = LocalDateTime.now();
        public String toString() { return name; }
    }

    public static class Wallet implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public String name; public String type = "نقدي"; public boolean active = true;
        public String toString() { return name; }
    }

    public enum InvType {
        SALE("فاتورة مبيعات"), PURCHASE("فاتورة مشتريات"), SALE_RETURN("مرتجع مبيعات"),
        PURCHASE_RETURN("مرتجع مشتريات"), QUOTE("عرض سعر"), ORDER("طلبية شراء");
        public final String title;
        InvType(String t) { title = t; }
        /** مستند بلا أثر: لا يدخل المخزون ولا الصندوق ولا الحسابات (عرض السعر وطلبية الشراء) */
        public boolean noEffect() { return this == QUOTE || this == ORDER; }
        public boolean customerSide() { return this == SALE || this == SALE_RETURN || this == QUOTE; }
        /** إشارة حركة المخزون */
        public int stockSign() {
            switch (this) { case SALE: case PURCHASE_RETURN: return -1; case PURCHASE: case SALE_RETURN: return 1; default: return 0; }
        }
        /** إشارة حركة الصندوق للمبلغ المدفوع */
        public int cashSign() {
            switch (this) { case SALE: case PURCHASE_RETURN: return 1; case PURCHASE: case SALE_RETURN: return -1; default: return 0; }
        }
        public String toString() { return title; }
    }

    public static class InvLine implements Serializable {
        private static final long serialVersionUID = 1L;
        public long productId; public String name = ""; public String barcode = "";
        public String unit = ""; public double factor = 1;
        public double qty = 1, price, discount, cost; // cost للحبة
        public String offer = "";      // اسم العرض المطبّق على السطر
        public boolean manualDisc;     // الخصم أُدخل يدوياً (لا يُستبدل بالعرض)
        public boolean taxable = true;
        public String expiry = "";     // تاريخ الصلاحية (فواتير الشراء) yyyy-MM-dd
        public double total() { return Ui.r(qty * price - discount); }
        public double pieces() { return qty * factor; }
        public InvLine copy() {
            InvLine l = new InvLine(); l.productId = productId; l.name = name; l.barcode = barcode; l.unit = unit;
            l.factor = factor; l.qty = qty; l.price = price; l.discount = discount; l.cost = cost; l.taxable = taxable;
            l.offer = offer; l.manualDisc = manualDisc; l.expiry = expiry;
            return l;
        }
    }

    public static class Invoice implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, no; public InvType type;
        public long partyId; public String partyName = "";
        // الفوترة الوطنية (JoFotara)
        public String einvUuid = "", einvStatus = "", einvQr = "", einvMsg = ""; public long einvIcv;
        public long warehouseId; // المستودع الذي خرجت منه / دخلت إليه البضاعة (0 = الرئيسي)
        public LocalDateTime date = LocalDateTime.now();
        public List<InvLine> lines = new ArrayList<>();
        public double subtotal, discount, tax, total, paid;
        public long walletId; public boolean cancelled; public String notes = ""; public String supplierNo = "";
        public long refId; // فاتورة أصلية (للمرتجع/التعديل/التحويل)
        public String user = ""; // الكاشير
        public double paid2; public long wallet2; // جزء مدفوع بطريقة ثانية (بطاقة/شيك) ضمن paid
        public double tendered;                   // المبلغ النقدي المستلم من الزبون
        public double pointsUsed, pointsValue;    // نقاط الولاء المستبدلة وقيمتها (ضمن discount)
        public double costTotal() { double c = 0; for (InvLine l : lines) c += l.cost * l.pieces(); return Ui.r(c); }
    }

    public static class Voucher implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, no; public boolean in; // in = قبض, !in = صرف
        public int partyKind; public long partyId; public double amount; public long walletId;
        public LocalDateTime date = LocalDateTime.now(); public long invoiceId; public boolean cancelled; public String notes = "";
        public String user = ""; // المستخدم الذي أنشأ السند
    }

    public static class ExpenseCat implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public String name; public String toString() { return name; }
    }

    public static class Expense implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public long catId; public double amount; public long walletId;
        public LocalDateTime date = LocalDateTime.now(); public String notes = ""; public boolean cancelled;
        public String user = ""; // المستخدم الذي سجّل المصروف
    }

    /** وردية كاشير: من فتح الدرج حتى الإغلاق والجرد */
    public static class Shift implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, no; public String user = "";
        public LocalDateTime opened = LocalDateTime.now(), closed;
        public double openingCash, expectedCash, countedCash, diff;
        public double sales, returns, cashSales, cardSales, creditSales, receipts, payments, expenses;
        public int invoiceCount;
        public String notes = "", counts = ""; // counts: عدد كل فئة نقدية
        public boolean isOpen() { return closed == null; }
    }

    /** عرض/خصم على صنف لفترة محددة */
    public static class Offer implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final int BUNDLE = 0, PERCENT = 1, SPECIAL = 2;
        public long id, productId; public String name = "";
        public int kind;             // BUNDLE: qty بسعر value — PERCENT: خصم value% — SPECIAL: سعر الحبة value
        public double qty = 1, value;
        public String from = java.time.LocalDate.now().toString(), to = java.time.LocalDate.now().plusDays(30).toString(); // yyyy-MM-dd
        public boolean active = true;
        public java.time.LocalDate fromDate() { try { return from == null || from.isEmpty() ? null : java.time.LocalDate.parse(from); } catch (Exception e) { return null; } }
        public java.time.LocalDate toDate() { try { return to == null || to.isEmpty() ? null : java.time.LocalDate.parse(to); } catch (Exception e) { return null; } }
        public boolean liveOn(java.time.LocalDate d) { java.time.LocalDate f = fromDate(), t = toDate(); return active && (f == null || !d.isBefore(f)) && (t == null || !d.isAfter(t)); }
        public String describe(String unit) {
            switch (kind) {
                case BUNDLE: return Ui.q(qty) + " " + unit + " بـ " + Ui.m(value);
                case PERCENT: return "خصم " + Ui.q(value) + "%";
                default: return "سعر خاص " + Ui.m(value);
            }
        }
    }

    public static class CashMove implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, walletId; public double amount; public LocalDateTime date = LocalDateTime.now();
        public String ref = "", notes = ""; public long refId;
    }

    public static class StockMove implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, productId; public double qty, cost; public LocalDateTime date = LocalDateTime.now();
        public String ref = "", notes = ""; public long refId;
        public long warehouseId; // 0 = المستودع الرئيسي
    }

    /** حساب في دليل الحسابات أنشأه المستخدم (الحسابات الأساسية يولّدها البرنامج تلقائياً) */
    public static class Account implements Serializable {
        private static final long serialVersionUID = 1L;
        public static final int ASSET = 0, LIABILITY = 1, EQUITY = 2, REVENUE = 3, EXPENSE = 4;
        public long id; public String code = "", name = ""; public int type; public boolean active = true; public String notes = "";
        public String toString() { return code + " - " + name; }
    }

    /** قيد يومية يدوي. الأسطر: "مفتاح الحساب|مدين|دائن|بيان" سطر لكل طرف */
    public static class JournalEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, no; public LocalDateTime date = LocalDateTime.now();
        public String notes = "", lines = "", user = ""; public boolean cancelled;
    }

    /** دفعة بضاعة بتاريخ صلاحية */
    public static class Batch implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, productId, invoiceId, warehouseId; public double qty; public String expiry = "";
        public LocalDateTime date = LocalDateTime.now(); public String notes = ""; public boolean closed;
    }

    /** أمر شراء لمورد. الأسطر: "productId:qty:cost;" */
    public static class PurchaseOrder implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, no, supplierId; public LocalDateTime date = LocalDateTime.now();
        public String lines = "", notes = "", user = "", status = "مفتوح"; public long invoiceId;
    }

    /** مستودع / فرع تخزين */
    public static class Warehouse implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public String name = ""; public boolean active = true; public String notes = "";
        public String toString() { return name; }
    }

    /** تحويل بضاعة بين مستودعين: الأسطر "رقم الصنف:الكمية;..." والحركات في سجل المخزون */
    public static class Transfer implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, no, fromWh, toWh; public LocalDateTime date = LocalDateTime.now();
        public String items = "", notes = "", user = ""; public boolean cancelled;
    }

    /** قيد على حساب عميل أو مورد. رصيد العميل = مدين - دائن، رصيد المورد = دائن - مدين */
    public static class Ledger implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id; public int partyKind; public long partyId; public double debit, credit;
        public LocalDateTime date = LocalDateTime.now(); public String ref = "", notes = ""; public long refId;
    }

    public static class Damage implements Serializable {
        private static final long serialVersionUID = 1L;
        public long id, productId; public double qty, cost; public LocalDateTime date = LocalDateTime.now();
        public String reason = ""; public boolean cancelled;
        public long warehouseId;
    }
}
