package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;

import com.market.Model;
import com.market.Model.InvType;
import com.market.ReportApi;

import java.util.ArrayList;
import java.util.List;

/** نفس أقسام القائمة الرئيسية في برنامج الكمبيوتر وشاشاتها */
public final class Menus {
    private Menus() {}
    public interface Act { void run(Activity a); }
    public static final class Item { public final String title; public final Act act; Item(String t, Act a) { title = t; act = a; } }
    public static final class Section {
        public final String title, icon; public final int color; public final List<Item> items = new ArrayList<>(); public final boolean adminOnly;
        Section(String t, String i, int c, boolean admin) { title = t; icon = i; color = c; adminOnly = admin; }
        Section a(String t, Act act) { items.add(new Item(t, act)); return this; }
    }

    static Act inv(InvType t) { return a -> a.startActivity(new Intent(a, InvoiceActivity.class).putExtra("type", t.name())); }
    static Act log(InvType t) { return a -> a.startActivity(new Intent(a, InvoicesActivity.class).putExtra("type", t.name())); }
    static Act rep(String name) { return a -> { int i = ReportApi.indexOf(name); if (i < 0) Ux.alert(a, "التقرير غير موجود: " + name); else a.startActivity(new Intent(a, ReportViewActivity.class).putExtra("index", i)); }; }
    static Act open(Class<?> c, String... kv) { return a -> { Intent i = new Intent(a, c); for (int k = 0; k + 1 < kv.length; k += 2) i.putExtra(kv[k], kv[k + 1]); a.startActivity(i); }; }
    static Act party(int kind, String what) { return a -> a.startActivity(new Intent(a, PartiesActivity.class).putExtra("kind", kind).putExtra("do", what)); }
    static Act note(String msg) { return a -> Ux.alert(a, msg); }

    public static List<Section> all() {
        List<Section> out = new ArrayList<>();
        out.add(new Section("المبيعات", "🛒", 0xFF16A34A, false)
                .a("نقطة البيع (فاتورة مبيعات)", inv(InvType.SALE)).a("مرتجع مبيعات", inv(InvType.SALE_RETURN)).a("عرض سعر", inv(InvType.QUOTE))
                .a("شاشة الزبون (تشغيل / إيقاف)", note("شاشة الزبون تعمل مع شاشة ثانية موصولة بالكمبيوتر، وهي متاحة في نسخة الكمبيوتر فقط."))
                .a("سجل فواتير المبيعات", log(InvType.SALE)).a("المبيعات الآجلة", rep("المبيعات الآجلة"))
                .a("المبيعات حسب طريقة الدفع", rep("المبيعات حسب طريقة الدفع (نقدي/بطاقة/شيك)")));
        out.add(new Section("المشتريات", "📦", 0xFF047857, false)
                .a("فاتورة مشتريات", inv(InvType.PURCHASE)).a("مرتجع مشتريات", inv(InvType.PURCHASE_RETURN)).a("سجل فواتير المشتريات", log(InvType.PURCHASE))
                .a("تسجيل طلبية (لا تدخل المخزون)", inv(InvType.ORDER)).a("سجل الطلبيات", log(InvType.ORDER)));
        out.add(new Section("المخزون", "🏷", 0xFF0D9488, false)
                .a("إضافة منتج جديد", open(ProductsActivity.class, "newCode", "")).a("منتج بالوحدات", open(ProductsActivity.class, "newCode", ""))
                .a("طباعة ملصقات الباركود", open(LabelsActivity.class)).a("العروض والخصومات", open(OffersActivity.class))
                .a("منتجات أقل من سعر التكلفة", rep("منتجات أقل من سعر التكلفة")).a("عرض المنتجات", open(ProductsActivity.class))
                .a("التصنيفات", open(ListsActivity.class, "mode", "cat")).a("تعديل أسعار المنتجات", open(ToolsActivity.class, "do", "bulk"))
                .a("استيراد من ملف إكسل", open(ImportActivity.class, "what", "products")).a("الجرد وتسوية الكميات", open(StocktakeActivity.class))
                .a("تواريخ الصلاحية", open(ToolsActivity.class, "do", "expiry")).a("اقتراح طلبية / أوامر الشراء", open(ToolsActivity.class, "do", "suggest"))
                .a("المستودعات", open(ListsActivity.class, "mode", "wh")).a("تحويل بين المستودعات", open(LogActivity.class, "mode", "transfer", "do", "new"))
                .a("سجل التحويلات", open(LogActivity.class, "mode", "transfer")));
        for (int k = 0; k < 2; k++) { int kind = k == 0 ? Model.CUSTOMER : Model.SUPPLIER; boolean c = k == 0;
            out.add(new Section(c ? "العملاء" : "الموردين", c ? "👥" : "🚚", c ? 0xFF7C3AED : 0xFFEA580C, false)
                    .a(c ? "إضافة عميل جديد" : "إضافة مورد جديد", party(kind, "add")).a("الأرصدة الافتتاحية", party(kind, "opening"))
                    .a(c ? "استيراد العملاء من إكسل" : "استيراد الموردين من إكسل", open(ImportActivity.class, "what", c ? "customers" : "suppliers"))
                    .a(c ? "المبالغ النقدية (سند قبض)" : "دفع مبلغ لمورد (سند صرف)", party(kind, "voucher"))
                    .a(c ? "الذمم (الفواتير الآجلة)" : "المبالغ المتبقية (آجل)", rep(c ? "الذمم (الفواتير الآجلة)" : "المتبقي للموردين (فواتير غير مسددة)"))
                    .a(c ? "فحص أرصدة العملاء" : "فحص أرصدة الموردين", rep(c ? "أرصدة العملاء" : "أرصدة الموردين"))
                    .a(c ? "عرض العملاء" : "عرض الموردين", party(kind, ""))); }
        out.add(new Section("المصروفات", "💸", 0xFFDC2626, false)
                .a("تسجيل مصروف", open(ExpensesActivity.class, "do", "add")).a("سجل المصروفات", open(ExpensesActivity.class)).a("بنود المصروفات", open(ListsActivity.class, "mode", "expcat")));
        out.add(new Section("الصندوق", "👛", 0xFFA16207, false)
                .a("الورديات وإغلاق الصندوق", open(ShiftsActivity.class)).a("أرصدة الصندوق والمحافظ", open(CashActivity.class))
                .a("تسوية الصندوق اليومية", rep("تسوية الصندوق اليومية")).a("إيداع / سحب", open(CashActivity.class, "do", "deposit"))
                .a("تحويل بين المحافظ", open(CashActivity.class, "do", "transfer")));
        out.add(new Section("الاستعلامات والتقارير", "📊", 0xFF2B6CB0, false)
                .a("مركز التقارير", open(ReportsActivity.class))
                .a("الفروع (ملخص كل الفروع)", note("ملخص الفروع يقرأ ملفات الفروع من مجلد مشترك على الكمبيوتر، وهو متاح في نسخة الكمبيوتر."))
                .a("تقرير رأس المال", rep("تقرير رأس المال")).a("إجمالي الضرائب حسب الصنف", rep("إجمالي الضرائب حسب الصنف")).a("حساب الزكاة", rep("حساب الزكاة")));
        Section sp = new Section("تقارير المبيعات والأرباح", "📈", 0xFF2C7A7B, false);
        String[][] spr = {{"تقرير المبيعات", "المبيعات - الكل"}, {"المبيعات الشهرية", null}, {"الفواتير الملغاة", null}, {"المبيعات حسب الكاشير", null}, {"الخصومات الممنوحة", null},
                {"مرتجعات المبيعات حسب الصنف", null}, {"المبيعات والأرباح - إجمالي", null}, {"الأرباح والخسائر", null}, {"مقارنة فترتين", "مقارنة فترتين (المبيعات والأرباح)"},
                {"الأرباح حسب الصنف", null}, {"الأرباح حسب التصنيف", null}, {"الأرباح لتصنيف", null}, {"الأرباح حسب العميل", null}, {"أصناف بربح منخفض أو بخسارة", null},
                {"الأرباح حسب رقم الفاتورة", null}};
        for (String[] r : spr) sp.a(r[0], rep(r[1] == null ? r[0] : r[1])); out.add(sp);
        Section st = new Section("تقارير المخزون", "📋", 0xFF2B6CB0, false);
        String[][] str = {{"جرد المخزون", null}, {"جرد المخزون - المنتجات المتوفرة", null}, {"جرد المخزون حسب التصنيف", null}, {"جرد مخزون لتصنيف", null}, {"الأصناف الأكثر مبيعاً", null},
                {"الأصناف الراكدة (بدون حركة بيع)", null}, {"المنتجات حسب تاريخ الانتهاء", null}, {"حركة صنف", null}, {"تقرير المنتجات التالفة", "المنتجات التالفة"}};
        for (String[] r : str) st.a(r[0], rep(r[1] == null ? r[0] : r[1])); out.add(st);
        Section ks = new Section("كشوف العملاء والموردين", "🧾", 0xFF6B46C1, false);
        String[][] ksr = {{"كشف حساب عميل", null}, {"أعمار ذمم العملاء", null}, {"مصادقة رصيد عميل", null}, {"فواتير العملاء - إجمالي", null}, {"إجمالي حسب الصنف لعميل", null},
                {"سداد العملاء حسب الدفع", "حركة السداد للعملاء حسب طريقة الدفع"}, {"كشف حساب مورد", null}, {"مصادقة رصيد مورد", null}, {"فواتير الموردين - إجمالي", null},
                {"إجمالي حسب الصنف لمورد", null}, {"المشتريات حسب الصنف", null}, {"تذبذب أسعار الشراء", null}, {"أعمار ذمم الموردين", null}, {"المتبقي للموردين", "المتبقي للموردين (فواتير غير مسددة)"}};
        for (String[] r : ksr) ks.a(r[0], rep(r[1] == null ? r[0] : r[1])); out.add(ks);
        out.add(new Section("المحاسبة", "📒", 0xFF2F855A, true)
                .a("دليل الحسابات", open(JournalActivity.class, "do", "chart")).a("القيود اليدوية", open(JournalActivity.class)).a("قيد يومية جديد", open(JournalActivity.class, "do", "new"))
                .a("دفتر اليومية", rep("دفتر اليومية")).a("دفتر الأستاذ العام", rep("دفتر الأستاذ العام")).a("ميزان المراجعة", rep("ميزان المراجعة"))
                .a("قائمة الدخل", rep("قائمة الدخل")).a("الميزانية العمومية", rep("الميزانية العمومية")));
        out.add(new Section("المعالجات المتقدمة", "🛠", 0xFF4A5568, false)
                .a("إلغاء السندات والمبالغ", open(LogActivity.class, "mode", "voucher")).a("مرتجعات المبيعات", log(InvType.SALE_RETURN)).a("مرتجعات المشتريات", log(InvType.PURCHASE_RETURN))
                .a("المنتجات التالفة", open(LogActivity.class, "mode", "damage")).a("شاشة عروض الأسعار", log(InvType.QUOTE)).a("استعلام عن سعر", open(ToolsActivity.class, "do", "price")));
        out.add(new Section("إعدادات النظام", "⚙", 0xFF475569, true)
                .a("المظهر والخط والثيمات", open(SettingsActivity.class, "sec", "look")).a("المستخدمون والصلاحيات", open(UsersActivity.class))
                .a("الإعدادات العامة", open(SettingsActivity.class, "sec", "general")).a("الضرائب", open(SettingsActivity.class, "sec", "tax"))
                .a("الميزان والنقاط وشاشة الزبون", open(SettingsActivity.class, "sec", "extras")).a("إدارة المحافظ", open(ListsActivity.class, "mode", "wallet"))
                .a("إعدادات الطابعة", open(SettingsActivity.class, "sec", "printer")).a("تفعيل البرنامج", open(SettingsActivity.class, "sec", "activation"))
                .a("النسخ الاحتياطي", open(BackupActivity.class)).a("الربط مع نسخة الكمبيوتر", open(LinkActivity.class))
                .a("الربط السحابي", open(BackupActivity.class)).a("الفوترة الوطنية (JoFotara)", open(SettingsActivity.class, "sec", "jof"))
                .a("معالجة البيانات", open(ToolsActivity.class)).a("استعادة الإعدادات الافتراضية", open(SettingsActivity.class, "sec", "reset"))
                .a("إقفال السنة المالية", open(SettingsActivity.class, "sec", "close")).a("حول البرنامج", open(SettingsActivity.class, "sec", "about")));
        return out;
    }
}
