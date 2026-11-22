package cn.yg.ocrshare;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private Cfg cfg;
    private TextView providerLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Cfg.load(this);

        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 30), Ui.dp(this, 16), Ui.dp(this, 30));
        root.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = Ui.card(this, Ui.CARD, Ui.LINE);
        TextView kicker = Ui.text(this, "SHARE-TARGET OCR", 10f, Ui.ACCENT, true, false);
        kicker.setLetterSpacing(0.22f);
        hero.addView(kicker);
        hero.addView(Ui.text(this, "截图取字", 25f, Ui.TEXT, false, true));
        TextView sub = Ui.text(this, "截图 → 分享到本应用 → 云端识别 → 分词选词", 11.5f, Ui.SUB, true, false);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        hero.addView(sub);
        providerLabel = Ui.text(this, "", 11f, Ui.OK, true, false);
        hero.addView(providerLabel);
        renderProvider();
        box.addView(hero);

        box.addView(Ui.section(this, "操作"));
        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        TextView pick = Ui.button(this, "选择图片识别", true);
        pick.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, REQ_PICK);
        });
        TextView set = Ui.button(this, "设置", false);
        set.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        acts.addView(pick);
        acts.addView(set);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(acts);
        box.addView(hsv);

        box.addView(Ui.section(this, "最近 3 次"));
        LinearLayout histCard = Ui.card(this, Ui.CARD, Ui.LINE);
        List<History.Entry> list = History.list(this);
        if (list.isEmpty()) {
            histCard.addView(Ui.text(this, "还没有识别记录", 12f, Ui.SUB, true, false));
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
            for (int i = 0; i < list.size(); i++) {
                History.Entry e = list.get(i);
                String preview = e.text.replace("\n", " ").trim();
                if (preview.length() > 14) {
                    preview = preview.substring(0, 14) + "…";
                }
                TextView row = Ui.text(this, fmt.format(new Date(e.at)) + "  " + preview + "  (" + e.text.length() + " 字)",
                        12.5f, Ui.TEXT, false, false);
                row.setSingleLine(true);
                row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
                final String text = e.text;
                row.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ResultActivity.class);
                    intent.putExtra(ResultActivity.EXTRA_TEXT, text);
                    startActivity(intent);
                });
                histCard.addView(row);
                if (i < list.size() - 1) {
                    TextView line = Ui.text(this, " ", 1f, Ui.LINE, false, false);
                    line.setBackgroundColor(Ui.LINE);
                    line.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    histCard.addView(line);
                }
            }
        }
        box.addView(histCard);

        box.addView(Ui.section(this, "怎么用"));
        LinearLayout guide = Ui.card(this, Ui.CARD, Ui.LINE);
        String[] steps = new String[]{
                "1. 截图后在相册/截图预览里点「分享」，选「截图取字」。",
                "2. 结果页只有一个分词视图：上下滑动原生滚动，轻点复制单词，横向拖过复制整段。",
                "3. 首页只保留最近 3 次，点任意一条可重新进入分词视图。",
                "4. 首次用 OCR.space 的 key 就能跑；要更准去设置里切「腾讯云·高精度」。"
        };
        for (String s : steps) {
            TextView tv = Ui.text(this, s, 12f, Ui.TEXT, false, false);
            tv.setPadding(0, Ui.dp(this, 6), 0, 0);
            guide.addView(tv);
        }
        box.addView(guide);

        Ui.applyInsets(root);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = Cfg.load(this);
        renderProvider();
    }

    private void renderProvider() {
        if (providerLabel == null) {
            return;
        }
        providerLabel.setText("服务：" + cfg.providerName()
                + (cfg.ready() ? " · 已配置" : " · 未配置"));
        providerLabel.setTextColor(cfg.ready() ? Ui.OK : Ui.WARN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Intent next = new Intent(this, ResultActivity.class);
                next.setData(uri);
                next.putExtra(Intent.EXTRA_STREAM, uri);
                next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(next);
            }
        }
    }
}
