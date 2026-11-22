package cn.yg.ocrshare;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 词块流式布局 + 手势：
 *  - 竖向拖动：滚动外层 ScrollView（不复制，避免"想滑动却误触发复制"）
 *  - 横向拖动：区间选择
 *  - 轻点：复制该词
 *
 * 手势统一在容器处理（onInterceptTouchEvent 直接拦下），不依赖子 View 的点击，
 * 这样滑动手势和点击判定不会互相打架。
 */
final class FlowSelectView extends ViewGroup {

    interface PickListener {
        void onPick(int first, int last, boolean fromDrag);
    }

    static final class Token {
        final WordSeg.Token src;
        final boolean breakBefore;

        Token(WordSeg.Token src, boolean breakBefore) {
            this.src = src;
            this.breakBefore = breakBefore;
        }
    }

    private final List<Token> tokens = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private final int gap;
    private final int touchSlop;
    private PickListener listener;
    private int anchor = -1;
    private int selMin = -1;
    private int selMax = -1;
    private float downX;
    private float downY;
    private float lastY;
    private boolean scrolling;

    FlowSelectView(Context c) {
        this(c, null);
    }

    FlowSelectView(Context c, AttributeSet attrs) {
        super(c, attrs);
        gap = Ui.dp(c, 4);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
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
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 全部手势归容器，子 TextView 不参与，避免"点/滑"判定互相覆盖
        return !chips.isEmpty();
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
                ViewParent p = getParent();
                if (p != null) {
                    p.requestDisallowInterceptTouchEvent(true);
                }
                anchor = hit(e.getX(), e.getY());
                selMin = anchor >= 0 ? anchor : -1;
                selMax = selMin;
                scrolling = false;
                downX = e.getX();
                downY = e.getY();
                lastY = downY;
                if (anchor >= 0) {
                    repaint();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dy = Math.abs(e.getY() - downY);
                float dx = Math.abs(e.getX() - downX);
                if (!scrolling && dy > touchSlop && dy > dx) {
                    scrolling = true;
                    // 从"选择"切到"滚动"：清掉高亮，避免看起来像选中了
                    selMin = -1;
                    selMax = -1;
                    repaint();
                }
                if (scrolling) {
                    ViewParent p = getParent();
                    if (p instanceof ScrollView) {
                        ((ScrollView) p).scrollBy(0, (int) (lastY - e.getY()));
                    }
                    lastY = e.getY();
                    return true;
                }
                int target = hit(e.getX(), e.getY());
                if (target >= 0) {
                    if (anchor < 0) {
                        anchor = target;
                    }
                    selMin = Math.min(anchor, target);
                    selMax = Math.max(anchor, target);
                    repaint();
                }
                lastY = e.getY();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                boolean wasScroll = scrolling;
                scrolling = false;
                if (!wasScroll && listener != null && selMin >= 0) {
                    listener.onPick(selMin, selMax, selMax > selMin);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                // 手势被打断（比如页面切换）：绝不当成一次点击去复制
                scrolling = false;
                selMin = -1;
                selMax = -1;
                repaint();
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
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
