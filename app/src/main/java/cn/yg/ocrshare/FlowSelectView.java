package cn.yg.ocrshare;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 文字方块流式布局 + 滑动区间选择（类似输入法里"复制后滑动选词"）。
 * DOWN 定锚点，MOVE 扩展区间，UP 回调选中区间。
 */
final class FlowSelectView extends ViewGroup {

    interface PickListener {
        void onPick(int first, int last, boolean fromDrag);
    }

    private final List<Token> tokens = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private final int gap;
    private PickListener listener;
    private int anchor = -1;
    private int selMin = -1;
    private int selMax = -1;

    static final class Token {
        final WordSeg.Token src;
        final boolean breakBefore;

        Token(WordSeg.Token src, boolean breakBefore) {
            this.src = src;
            this.breakBefore = breakBefore;
        }
    }

    FlowSelectView(Context c) {
        this(c, null);
    }

    FlowSelectView(Context c, AttributeSet attrs) {
        super(c, attrs);
        gap = Ui.dp(c, 4);
        setClipChildren(false);
    }

    void setPickListener(PickListener l) {
        this.listener = l;
    }

    void setTokens(List<WordSeg.Token> list) {
        removeAllViews();
        tokens.clear();
        chips.clear();
        anchor = -1;
        selMin = -1;
        selMax = -1;
        boolean pendingBreak = false;
        for (WordSeg.Token t : list) {
            if (t.lineBreak) {
                pendingBreak = true;
                continue;
            }
            tokens.add(new Token(t, pendingBreak));
            pendingBreak = false;
            TextView tv = new TextView(getContext());
            tv.setText(t.text);
            tv.setTextSize(13.5f);
            tv.setPadding(Ui.dp(getContext(), 9), Ui.dp(getContext(), 5),
                    Ui.dp(getContext(), 9), Ui.dp(getContext(), 5));
            tv.setSingleLine(true);
            tv.setTextColor(Ui.TEXT);
            tv.setBackground(Ui.round(getContext(), Ui.CARD_ALT, Ui.LINE, 9f));
            final int index = chips.size();
            tv.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        anchor = index;
                        selMin = index;
                        selMax = index;
                        repaint();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int target = hitRaw(ev.getRawX(), ev.getRawY());
                        if (target >= 0 && anchor >= 0) {
                            selMin = Math.min(anchor, target);
                            selMax = Math.max(anchor, target);
                            repaint();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (listener != null && selMin >= 0) {
                            listener.onPick(selMin, selMax, selMax > selMin);
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            });
            chips.add(tv);
            addView(tv);
        }
        repaint();
        requestLayout();
    }

    int size() {
        return tokens.size();
    }

    String textOf(int first, int last) {
        if (tokens.isEmpty()) {
            return "";
        }
        int a = Math.max(0, Math.min(first, last));
        int b = Math.min(tokens.size() - 1, Math.max(first, last));
        StringBuilder sb = new StringBuilder();
        for (int i = a; i <= b; i++) {
            Token tk = tokens.get(i);
            if (tk.breakBefore && sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(tk.src.text);
        }
        return sb.toString();
    }

    void clearSelection() {
        anchor = -1;
        selMin = -1;
        selMax = -1;
        repaint();
    }

    private void repaint() {
        for (int i = 0; i < chips.size(); i++) {
            boolean on = selMin >= 0 && i >= selMin && i <= selMax;
            TextView tv = chips.get(i);
            if (on) {
                tv.setTextColor(Color.parseColor("#041513"));
                tv.setBackground(Ui.round(getContext(), Ui.ACCENT, Color.TRANSPARENT, 9f));
            } else {
                tv.setTextColor(Ui.TEXT);
                tv.setBackground(Ui.round(getContext(), Ui.CARD_ALT, Ui.LINE, 9f));
            }
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) {
            width = Ui.dp(getContext(), 320);
        }
        int avail = width - getPaddingLeft() - getPaddingRight();
        int lineW = 0;
        int lineH = 0;
        int totalH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            TextView tv = (TextView) getChildAt(i);
            tv.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = tv.getMeasuredWidth();
            boolean forceBreak = i < tokens.size() && tokens.get(i).breakBefore;
            if (lineW > 0 && (forceBreak || lineW + gap + w > avail)) {
                totalH += lineH + gap;
                lineW = 0;
                lineH = 0;
            }
            lineW = lineW == 0 ? w : lineW + gap + w;
            lineH = Math.max(lineH, tv.getMeasuredHeight());
        }
        totalH += lineH;
        totalH += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, resolveSize(totalH, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int avail = getWidth() - getPaddingLeft() - getPaddingRight();
        if (getChildCount() > 0) {
            int[] p = new int[2];
            getChildAt(0).getLocationOnScreen(p);
            android.util.Log.i("FlowSel", "layout w=" + getWidth() + " h=" + getHeight()
                    + " chip0=" + p[0] + "," + p[1] + " chipH=" + getChildAt(0).getHeight());
        }
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            TextView tv = (TextView) getChildAt(i);
            int w = tv.getMeasuredWidth();
            int h = tv.getMeasuredHeight();
            boolean forceBreak = i < tokens.size() && tokens.get(i).breakBefore;
            if (x > getPaddingLeft() && (forceBreak || x + w > getPaddingLeft() + avail)) {
                x = getPaddingLeft();
                y += lineH + gap;
                lineH = 0;
            }
            tv.layout(x, y, x + w, y + h);
            x += w + gap;
            lineH = Math.max(lineH, h);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (chips.isEmpty()) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // 按在词块间隙也吃下事件：手指滑进词块后再定锚点，避免"点空了没反应"
                int i = hit(e.getX(), e.getY());
                android.util.Log.i("FlowSel", "DOWN x=" + e.getX() + " y=" + e.getY() + " hit=" + i
                        + " children=" + getChildCount());
                anchor = i;
                if (i >= 0) {
                    selMin = i;
                    selMax = i;
                    repaint();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int i = hit(e.getX(), e.getY());
                if (i >= 0) {
                    if (anchor < 0) {
                        anchor = i;
                    }
                    selMin = Math.min(anchor, i);
                    selMax = Math.max(anchor, i);
                    repaint();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean drag = selMin >= 0 && selMax > selMin;
                android.util.Log.i("FlowSel", "UP sel=" + selMin + ".." + selMax + " drag=" + drag);
                if (listener != null && selMin >= 0) {
                    listener.onPick(selMin, selMax, drag);
                }
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    private int hitRaw(float rawX, float rawY) {
        int[] loc = new int[2];
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View v = getChildAt(i);
            v.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX <= loc[0] + v.getWidth()
                    && rawY >= loc[1] && rawY <= loc[1] + v.getHeight()) {
                return i;
            }
        }
        return -1;
    }

    private int hit(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            TextView tv = (TextView) getChildAt(i);
            if (x >= tv.getLeft() && x <= tv.getRight() && y >= tv.getTop() && y <= tv.getBottom()) {
                return i;
            }
        }
        return -1;
    }
}
