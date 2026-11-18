package com.marketpro.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

/** بديل AlertDialog.Builder يبني نافذة بنفس تصميم نوافذ البرنامج (Ux.Win) */
public final class Dlg {
    final Context c; String title, message; View view; boolean cancelable = true;
    String pos, neu, neg; DialogInterface.OnClickListener posL, neuL, negL;
    String[] multi; boolean[] checked; DialogInterface.OnMultiChoiceClickListener multiL;

    public Dlg(Context c) { this.c = c; }
    public Dlg setTitle(CharSequence t) { title = t == null ? null : t.toString(); return this; }
    public Dlg setMessage(CharSequence m) { message = m == null ? null : m.toString(); return this; }
    public Dlg setView(View v) { view = v; return this; }
    public Dlg setCancelable(boolean b) { cancelable = b; return this; }
    public Dlg setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { pos = t.toString(); posL = l; return this; }
    public Dlg setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { neu = t.toString(); neuL = l; return this; }
    public Dlg setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { neg = t.toString(); negL = l; return this; }
    public Dlg setMultiChoiceItems(String[] items, boolean[] on, DialogInterface.OnMultiChoiceClickListener l) { multi = items; checked = on; multiL = l; return this; }

    public AlertDialog create() {
        Ux.Win w = new Ux.Win(c, title == null && view == null ? "MarketPro" : title, null);
        if (message != null) w.msg(message);
        if (view != null) { if (view.getParent() == null) w.body.addView(view); }
        if (multi != null) for (int i = 0; i < multi.length; i++) { final int k = i; CheckBox x = new CheckBox(c); x.setText(multi[i]); x.setTextColor(Ux.TEXT); x.setChecked(checked[i]);
            x.setOnCheckedChangeListener((b, v) -> { checked[k] = v; if (multiL != null) multiL.onClick(w.d, k, v); }); w.body.addView(x); }
        if (pos != null) w.btn(pos, Ux.PRIMARY, () -> { if (posL != null) posL.onClick(w.d, DialogInterface.BUTTON_POSITIVE); });
        if (neu != null) w.btn(neu, Ux.TEAL, () -> { if (neuL != null) neuL.onClick(w.d, DialogInterface.BUTTON_NEUTRAL); });
        if (neg != null) w.btn(neg, Ux.GRAY, () -> { if (negL != null) negL.onClick(w.d, DialogInterface.BUTTON_NEGATIVE); });
        if (w.buttons.getChildCount() == 0) w.buttons.setVisibility(View.GONE);
        w.d.setCancelable(cancelable);
        return w.d;
    }
    public AlertDialog show() { AlertDialog d = create(); d.show(); return d; }
}
