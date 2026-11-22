package cn.yg.ocrshare;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 最近 3 条识别记录（首页列表用，点进去复用结果页的分词视图）。 */
final class History {

    static final class Entry {
        final String text;
        final long at;

        Entry(String text, long at) {
            this.text = text;
            this.at = at;
        }
    }

    private static final String PREF = "history";
    private static final String KEY = "items";
    private static final int MAX = 3;

    private History() {
    }

    static void add(Context c, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        List<Entry> list = list(c);
        List<Entry> next = new ArrayList<>();
        next.add(new Entry(text, System.currentTimeMillis()));
        for (Entry e : list) {
            if (e.text.equals(text)) {
                continue;
            }
            if (next.size() >= MAX) {
                break;
            }
            next.add(e);
        }
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : next) {
                JSONObject o = new JSONObject();
                o.put("t", e.text);
                o.put("at", e.at);
                arr.put(o);
            }
        } catch (Throwable ignored) {
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, arr.toString())
                .apply();
    }

    static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length() && out.size() < MAX; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String t = o.optString("t", "");
                if (!t.isEmpty()) {
                    out.add(new Entry(t, o.optLong("at", 0L)));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
