package cn.yg.ocrshare;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 分词视图：一个 TextView + Span（原生滚动），词元之间插窄空格并加底色，视觉上仍是一词一块。
 *
 * tokens[i] = {rawStart, rawEnd, dispStart, dispEnd}
 * - raw*  复制原文时使用（不含人工插入的窄空格）
 * - disp* 命中测试 / 高亮使用
 *
 * 关键点（代码审查后修正）：
 * 1. setText 必须用 BufferType.SPANNABLE，否则 getText() 拿到的是不可变 SpannedString，高亮永远画不出来；
 * 2. 复制时按 raw 间隙还原换行（保留空行）与空格（英文/数字不被拼成一坨）；
 * 3. 竖向拖动交给外层 ScrollView（收到 CANCEL 只清高亮），横向拖动才抢事件；
 * 4. 选区没变化就不重复增删 Span，长文本拖动不掉帧。
 */
public class TokenTextView extends TextView {

    private static final char SEP = '\u2009';
    private static final int TOKEN_BG = Color.parseColor("#1B2230");
    private static final int MAX_BG_SPANS = 1500;

    interface OnPick {
        void onPick(String picked);
    }

    private final List<int[]> tokens = new ArrayList<>();
    private String raw = "";
    private OnPick listener;
    private int slop;
    private int anchor = -1;
    private int selA = -1;
    private int selB = -1;
    private int paintedA = -2;
    private int paintedB = -2;
    private float downX;
    private float downY;
    private boolean dragging;
    private BackgroundColorSpan bgSpan;
    private ForegroundColorSpan fgSpan;

    public TokenTextView(Context c) {
        this(c, null);
    }

    public TokenTextView(Context c, AttributeSet attrs) {
        super(c, attrs);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setTextSize(14.5f);
        setTextColor(Ui.TEXT);
        setLineSpacing(Ui.dp(c, 7), 1f);
        setTextIsSelectable(false);
    }

    void setPickListener(OnPick l) {
        listener = l;
    }

    void setContent(String text) {
        raw = text == null ? "" : text;
        tokens.clear();
        StringBuilder disp = new StringBuilder();
        List<WordSeg.Token> list = WordSeg.split(raw);
        int lastEnd = 0;
        for (WordSeg.Token t : list) {
            if (t.lineBreak || t.end <= t.start) {
                continue;
            }
            if (disp.length() > 0) {
                int nl = countNewlines(raw, lastEnd, t.start);
                for (int k = 0; k < Math.min(nl, 2); k++) {
                    disp.append('\n');
                }
            }
            int ds = disp.length();
            disp.append(raw, t.start, t.end);
            int de = disp.length();
            tokens.add(new int[]{t.start, t.end, ds, de});
            disp.append(SEP);
            lastEnd = t.end;
        }

        SpannableString sp = new SpannableString(disp.toString());
        if (tokens.size() <= MAX_BG_SPANS) {
            for (int[] tk : tokens) {
                sp.setSpan(new BackgroundColorSpan(TOKEN_BG), tk[2], tk[3],
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        // 必须是 SPANNABLE，否则 TextView 会拷贝成不可变 SpannedString，运行期加的 span 全丢
        setText(sp, BufferType.SPANNABLE);
        clearSelection();
    }

    int tokenCount() {
        return tokens.size();
    }

    private static int countNewlines(String s, int from, int to) {
        int n = 0;
        int a = Math.max(0, from);
        int b = Math.min(s.length(), to);
        for (int i = a; i < b; i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private void clearSelection() {
        anchor = -1;
        selA = -1;
        selB = -1;
        paintedA = -2;
        paintedB = -2;
        removeSpans();
        invalidate();
    }

    private void removeSpans() {
        Spannable sp = spannable();
        if (sp == null) {
            return;
        }
        if (bgSpan != null) {
            sp.removeSpan(bgSpan);
            bgSpan = null;
        }
        if (fgSpan != null) {
            sp.removeSpan(fgSpan);
            fgSpan = null;
        }
    }

    private Spannable spannable() {
        CharSequence cs = getText();
        return cs instanceof Spannable ? (Spannable) cs : null;
    }

    private void paintSelection(int a, int b) {
        if (tokens.isEmpty() || a < 0 || b < 0) {
            removeSpans();
            paintedA = -2;
            paintedB = -2;
            invalidate();
            return;
        }
        if (a == paintedA && b == paintedB) {
            return;
        }
        int ia = Math.max(0, Math.min(a, b));
        int ib = Math.min(tokens.size() - 1, Math.max(a, b));
        int ds = tokens.get(ia)[2];
        int de = tokens.get(ib)[3];
        Spannable sp = spannable();
        if (sp == null || ds >= de) {
            return;
        }
        removeSpans();
        bgSpan = new BackgroundColorSpan(Ui.ACCENT);
        fgSpan = new ForegroundColorSpan(Color.parseColor("#041513"));
        sp.setSpan(bgSpan, ds, de, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(fgSpan, ds, de, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        paintedA = a;
        paintedB = b;
        invalidate();
    }

    private int tokenAt(int offset) {
        if (offset < 0 || tokens.isEmpty()) {
            return -1;
        }
        int lo = 0;
        int hi = tokens.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int[] t = tokens.get(mid);
            if (offset < t[2]) {
                hi = mid - 1;
            } else if (offset >= t[3]) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i)[3] <= offset) {
                return i;
            }
        }
        return 0;
    }

    /** 复制：按原文间隙还原换行与空格，保证英文/数字不被拼成一个词。 */
    private String pick(int a, int b) {
        if (tokens.isEmpty() || a < 0 || b < 0) {
            return "";
        }
        int ia = Math.max(0, Math.min(a, b));
        int ib = Math.min(tokens.size() - 1, Math.max(a, b));
        StringBuilder sb = new StringBuilder();
        for (int i = ia; i <= ib; i++) {
            int[] t = tokens.get(i);
            if (i > ia) {
                int prevEnd = tokens.get(i - 1)[1];
                int nl = countNewlines(raw, prevEnd, t[0]);
                if (nl > 0) {
                    for (int k = 0; k < nl; k++) {
                        sb.append('\n');
                    }
                } else if (t[0] > prevEnd) {
                    // 原本有空格/缩进：折叠成一个空格，避免 "PowerPointPresentation"
                    sb.append(' ');
                }
            }
            sb.append(raw, t[0], t[1]);
        }
        return sb.toString();
    }

    private void disallowParent(boolean disallow) {
        ViewParent p = getParent();
        if (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (tokens.isEmpty()) {
                    return false;
                }
                anchor = tokenAt(getOffsetForPosition(e.getX(), e.getY()));
                if (anchor < 0) {
                    return false; // 点在空白/padding：交给外层滚动
                }
                selA = anchor;
                selB = anchor;
                paintedA = -2;
                paintedB = -2;
                dragging = false;
                downX = e.getX();
                downY = e.getY();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(e.getX() - downX);
                float dy = Math.abs(e.getY() - downY);
                if (!dragging && dx > slop && dx > dy) {
                    dragging = true;
                    disallowParent(true); // 横向选择期间别让 ScrollView 抢走手势
                }
                if (dragging) {
                    int t = tokenAt(getOffsetForPosition(e.getX(), e.getY()));
                    if (t >= 0 && anchor >= 0) {
                        selA = Math.min(anchor, t);
                        selB = Math.max(anchor, t);
                        paintSelection(selA, selB);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragging && anchor >= 0) {
                    selA = anchor;
                    selB = anchor;
                }
                if (listener != null && selA >= 0) {
                    String picked = pick(selA, selB);
                    if (!picked.trim().isEmpty()) {
                        listener.onPick(picked);
                    }
                }
                disallowParent(false);
                clearSelection();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                disallowParent(false);
                clearSelection();
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }
}
