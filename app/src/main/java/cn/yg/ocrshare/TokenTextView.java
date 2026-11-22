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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 分词视图：一个 TextView + Span 渲染（原生滚动），但每个词元之间插入「窄空格」并加底色，
 * 保证视觉上仍是"一词一块"，不会糊成一段。
 *
 *  tokens[i] = {rawStart, rawEnd, dispStart, dispEnd}
 *  - raw*  用于复制原文（不含人工插入的窄空格）
 *  - disp* 用于命中测试与高亮
 */
public class TokenTextView extends TextView {

    /** 词元之间的分隔符：U+2009 THIN SPACE，占位很小但足够把相邻色块分开。 */
    private static final char SEP = '\u2009';
    private static final int TOKEN_BG = Color.parseColor("#1B2230");

    interface OnPick {
        void onPick(String picked);
    }

    private final List<int[]> tokens = new ArrayList<>();
    private final List<BackgroundColorSpan> tokenBgs = new ArrayList<>();
    private String raw = "";
    private String display = "";
    private OnPick listener;
    private int slop;
    private int anchor = -1;
    private int selA = -1;
    private int selB = -1;
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
        tokenBgs.clear();
        StringBuilder disp = new StringBuilder();
        List<WordSeg.Token> list = WordSeg.split(raw);
        boolean pendingBreak = false;
        for (WordSeg.Token t : list) {
            if (t.lineBreak) {
                pendingBreak = true;
                continue;
            }
            if (t.end <= t.start) {
                continue;
            }
            if (pendingBreak && disp.length() > 0) {
                disp.append('\n');
            }
            pendingBreak = false;
            int ds = disp.length();
            disp.append(raw, t.start, t.end);
            int de = disp.length();
            tokens.add(new int[]{t.start, t.end, ds, de});
            disp.append(SEP);
        }
        display = disp.toString();

        SpannableString sp = new SpannableString(display);
        for (int[] tk : tokens) {
            BackgroundColorSpan bg = new BackgroundColorSpan(TOKEN_BG);
            sp.setSpan(bg, tk[2], tk[3], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tokenBgs.add(bg);
        }
        setText(sp);
        clearSelection();
    }

    int tokenCount() {
        return tokens.size();
    }

    private void clearSelection() {
        anchor = -1;
        selA = -1;
        selB = -1;
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
        if (tokens.isEmpty()) {
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
        // 落在窄空格/换行上：取前一个词元
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i)[3] <= offset) {
                return i;
            }
        }
        return 0;
    }

    private String pick(int a, int b) {
        if (tokens.isEmpty()) {
            return "";
        }
        int ia = Math.max(0, Math.min(a, b));
        int ib = Math.min(tokens.size() - 1, Math.max(a, b));
        StringBuilder sb = new StringBuilder();
        for (int i = ia; i <= ib; i++) {
            int[] t = tokens.get(i);
            if (sb.length() > 0) {
                int prevEnd = tokens.get(i - 1)[1];
                if (raw.substring(prevEnd, t[0]).contains("\n")) {
                    sb.append('\n');
                }
            }
            sb.append(raw, t[0], t[1]);
        }
        return sb.toString();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (tokens.isEmpty()) {
                    return false;
                }
                anchor = tokenAt(getOffsetForPosition(e.getX(), e.getY()));
                selA = anchor;
                selB = anchor;
                dragging = false;
                downX = e.getX();
                downY = e.getY();
                paintSelection(anchor, anchor);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(e.getX() - downX);
                float dy = Math.abs(e.getY() - downY);
                if (!dragging && dx > slop && dx > dy) {
                    dragging = true;
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
                clearSelection();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                clearSelection();
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }
}
