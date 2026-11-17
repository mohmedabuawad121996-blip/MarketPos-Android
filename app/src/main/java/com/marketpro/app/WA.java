package com.marketpro.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.market.WhatsApp;

/** إرسال رسالة واتساب جاهزة لرقم العميل */
public final class WA {
    private WA() {}
    public static void send(Activity a, String phone, String text) {
        String ph = phone == null ? "" : phone.trim();
        if (WhatsApp.normalize(ph).length() < 8) { Ux.input(a, "رقم الواتساب (مثل 0791234567)", "الرقم", ph, true, s -> { if (WhatsApp.normalize(s).length() >= 8) send(a, s, text); }); return; }
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WhatsApp.link(ph, text)))); }
        catch (Exception e) { Share.text(a, "واتساب", text); }
    }
}
