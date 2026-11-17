package com.mono.inventory;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = Color.rgb(247,247,242);
    public static final int INK = Color.rgb(34,48,45);
    public static final int MUTED = Color.rgb(111,122,117);
    public static final int GREEN = Color.rgb(49,94,88);
    private Ui() {}
    public static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    public static LinearLayout vertical(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static TextView text(Context c, String s, int sp, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(INK);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    public static GradientDrawable shape(int color, int radius, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, radius)); return g;
    }
    public static void pad(View v, Context c, int a, int b, int d, int e) { v.setPadding(dp(c,a), dp(c,b), dp(c,d),dp(c,e)); }
    public static LinearLayout.LayoutParams lp(Context c, int w, int h, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w<0?w:dp(c,w), h<0?h:dp(c,h)); p.bottomMargin=dp(c,bottom); return p;
    }
}
