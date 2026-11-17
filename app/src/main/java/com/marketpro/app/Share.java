package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.market.Model.InvLine;
import com.market.Model.Invoice;
import com.market.Store;
import com.market.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** مشاركة الفواتير والتقارير (واتساب، بريد، حفظ في الملفات...) */
public final class Share {
    private Share() {}

    public static void text(Activity a, String title, String body) {
        Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_SUBJECT, title); i.putExtra(Intent.EXTRA_TEXT, body);
        a.startActivity(Intent.createChooser(i, title));
    }

    public static String invoiceText(Invoice inv) {
        StringBuilder b = new StringBuilder();
        b.append(Store.db.get("storeName", "")).append('\n');
        String phone = Store.db.get("phone", ""); if (!phone.isEmpty()) b.append(phone).append('\n');
        b.append(inv.type.title).append(" رقم ").append(inv.no).append('\n').append(Ui.dt(inv.date)).append('\n');
        if (inv.partyId != 0) b.append(Store.db.partyName(inv.partyId)).append('\n');
        b.append("------------------------\n");
        for (InvLine l : inv.lines) {
            b.append(l.name).append('\n').append("  ").append(Ui.q(l.qty)).append(' ').append(l.unit).append(" × ").append(Ui.m(l.price));
            if (l.discount > 0) b.append(" - ").append(Ui.m(l.discount));
            b.append(" = ").append(Ui.m(l.total())).append('\n');
        }
        b.append("------------------------\n");
        if (inv.discount > 0) b.append("الخصم: ").append(Ui.m(inv.discount)).append('\n');
        if (inv.tax > 0) b.append("الضريبة: ").append(Ui.m(inv.tax)).append('\n');
        b.append("الإجمالي: ").append(Ui.m(inv.total)).append(' ').append(Store.db.get("currency", "")).append('\n');
        b.append("المدفوع: ").append(Ui.m(inv.paid)).append('\n');
        if (inv.total - inv.paid > 0.0005) b.append("المتبقي: ").append(Ui.m(inv.total - inv.paid)).append('\n');
        String f = Store.db.get("footer", ""); if (!f.isEmpty()) b.append('\n').append(f);
        return b.toString();
    }
    public static void invoice(Activity a, Invoice inv) { text(a, inv.type.title + " " + inv.no, invoiceText(inv)); }

    /** ملف CSV يفتحه إكسل (مع BOM ليظهر العربي صحيحاً) */
    public static void csv(Activity a, String name, String[] head, List<String[]> rows) {
        StringBuilder b = new StringBuilder("﻿");
        b.append(line(head));
        for (String[] r : rows) b.append(line(r));
        file(a, name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".csv", "text/csv", b.toString().getBytes(StandardCharsets.UTF_8));
    }
    static String line(String[] r) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length; i++) { if (i > 0) b.append(','); String s = r[i] == null ? "" : r[i];
            b.append('"').append(s.replace("\"", "\"\"")).append('"'); }
        return b.append("\r\n").toString();
    }

    public static void file(Activity a, String name, String mime, byte[] data) {
        try {
            File dir = new File(a.getCacheDir(), "share"); dir.mkdirs();
            File f = new File(dir, name);
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
            share(a, f, mime);
        } catch (Exception e) { Ux.alert(a, "تعذّرت المشاركة: " + e.getMessage()); }
    }
    public static void share(Activity a, File f, String mime) {
        Uri u = FileProvider.getUriForFile(a, a.getPackageName() + ".files", f);
        Intent i = new Intent(Intent.ACTION_SEND); i.setType(mime); i.putExtra(Intent.EXTRA_STREAM, u); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        a.startActivity(Intent.createChooser(i, f.getName()));
    }
}
