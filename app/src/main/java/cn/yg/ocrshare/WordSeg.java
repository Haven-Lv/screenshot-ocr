package cn.yg.ocrshare;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 离线分词：优先用 Android 自带的 ICU 词典分词（BreakIterator(Locale.zh)），
 * 它带 CJ 词典，中文能切出"微信/输入法/复制"这种词；ICU 不可用时退化为字符串规则。
 */
final class WordSeg {

    static final class Token {
        final String text;
        final int start;
        final int end;
        final boolean lineBreak;

        Token(String text, int start, int end, boolean lineBreak) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.lineBreak = lineBreak;
        }
    }

    private WordSeg() {
    }

    static List<Token> split(String text) {
        List<Token> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        try {
            tokens = icuSplit(text);
        } catch (Throwable ignored) {
            tokens = ruleSplit(text);
        }
        if (tokens.isEmpty()) {
            tokens = ruleSplit(text);
        }
        return tokens;
    }

    private static List<Token> icuSplit(String text) {
        List<Token> out = new ArrayList<>();
        android.icu.text.BreakIterator bi =
                android.icu.text.BreakIterator.getWordInstance(Locale.SIMPLIFIED_CHINESE);
        bi.setText(text);
        int start = bi.first();
        for (int end = bi.next(); end != android.icu.text.BreakIterator.DONE; start = end, end = bi.next()) {
            String piece = text.substring(start, end);
            boolean line = piece.equals("\n") || piece.equals("\r\n") || piece.equals("\r");
            if (line) {
                // 换行只作为分段标记，界面上不再画成 ↵ 词块
                out.add(new Token("", start, end, true));
            } else if (!piece.trim().isEmpty()) {
                out.add(new Token(piece, start, end, false));
            }
        }
        // 连续空行折叠成一个换行 token
        List<Token> merged = new ArrayList<>(out.size());
        for (Token t : out) {
            if (t.lineBreak && !merged.isEmpty() && merged.get(merged.size() - 1).lineBreak) {
                continue;
            }
            merged.add(t);
        }
        return merged;
    }

    /** 兜底：中文按 2 字切，英文/数字按连续段，标点单独。 */
    private static List<Token> ruleSplit(String text) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                out.add(new Token("↵", i, i + 1, true));
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int j = i;
            if (isCjk(c)) {
                j = Math.min(n, i + 2);
            } else if (Character.isLetterOrDigit(c)) {
                while (j < n && Character.isLetterOrDigit(text.charAt(j)) && !isCjk(text.charAt(j))) {
                    j++;
                }
            } else {
                j = i + 1;
            }
            out.add(new Token(text.substring(i, j), i, j, false));
            i = j;
        }
        return out;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }
}
