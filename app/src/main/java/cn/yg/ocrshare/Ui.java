package cn.yg.ocrshare;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.parseColor("#0E1116");
    static final int CARD = Color.parseColor("#161A22");
    static final int CARD_ALT = Color.parseColor("#1C2230");
    static final int LINE = Color.parseColor("#242B3A");
    static final int ACCENT = Color.parseColor("#00E5C9");
    static final int ACCENT_DARK = Color.parseColor("#08302E");
    static final int TEXT = Color.parseColor("#E8EDF6");
    static final int SUB = Color.parseColor("#8B95A9");
    static final int WARN = Color.parseColor("#FFB020");
    static final int ERR = Color.parseColor("#FF5C7A");
    static final int OK = Color.parseColor("#38E08A");

    private Ui() {
    }

    /** targetSdk 36 强制 edge-to-edge：把状态栏/导航栏/输入法的高度加回根布局 padding。 */
    static void applyInsets(final View root) {
        if (root == null) {
            return;
        }
        final int baseTop = root.getPaddingTop();
        final int baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int t;
            int b;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = ins.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                t = i.top;
                b = i.bottom;
            } else {
                t = ins.getSystemWindowInsetTop();
                b = ins.getSystemWindowInsetBottom();
            }
            v.setPadding(v.getPaddingLeft(), baseTop + t, v.getPaddingRight(), baseBottom + b);
            return ins;
        });
        root.requestApplyInsets();
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != Color.TRANSPARENT) {
            d.setStroke(dp(c, 1f), stroke);
        }
        return d;
    }

    static LinearLayout card(Context c, int fill, int stroke) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackground(round(c, fill, stroke, 18f));
        ll.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 14);
        ll.setLayoutParams(lp);
        return ll;
    }

    static TextView text(Context c, String s, float sp, int color, boolean mono, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (mono) {
            tv.setTypeface(Typeface.MONOSPACE);
        } else if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return tv;
    }

    static TextView section(Context c, String title) {
        TextView tv = text(c, title, 11f, SUB, false, true);
        tv.setLetterSpacing(0.18f);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 22);
        lp.leftMargin = dp(c, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    static TextView chip(Context c, String s, int color) {
        TextView tv = text(c, s, 10.5f, color, true, false);
        tv.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        tv.setBackground(round(c, ACCENT_DARK, Color.TRANSPARENT, 20f));
        return tv;
    }

    /** 切换按钮的选中态：选中=实心强调色，未选中=描边。 */
    static void setButtonActive(Context c, TextView tv, boolean active) {
        if (tv == null) {
            return;
        }
        if (active) {
            tv.setTextColor(Color.parseColor("#041513"));
            tv.setBackground(round(c, ACCENT, Color.TRANSPARENT, 22f));
        } else {
            tv.setTextColor(TEXT);
            tv.setBackground(round(c, CARD_ALT, LINE, 22f));
        }
    }

    static TextView button(Context c, String s, boolean primary) {
        TextView tv = text(c, s, 12.5f, primary ? Color.parseColor("#041513") : TEXT, false, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        tv.setBackground(primary ? round(c, ACCENT, Color.TRANSPARENT, 22f)
                : round(c, CARD_ALT, LINE, 22f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 8);
        lp.topMargin = dp(c, 8);
        tv.setLayoutParams(lp);
        return tv;
    }
}
