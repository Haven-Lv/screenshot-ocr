package cn.yg.ocrshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1001;
    private Cfg cfg;

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
        TextView title = Ui.text(this, "截图取字", 25f, Ui.TEXT, false, true);
        hero.addView(title);
        TextView sub = Ui.text(this, "截图 → 分享到本应用 → 云端识别 → 复制/分享文字", 11.5f, Ui.SUB, true, false);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        hero.addView(sub);
        TextView prov = Ui.text(this, "服务：" + cfg.providerName()
                + (cfg.ready() ? " · 已配置" : " · 未配置"), 11f,
                cfg.ready() ? Ui.OK : Ui.WARN, true, false);
        hero.addView(prov);
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
        set.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        acts.addView(pick);
        acts.addView(set);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(acts);
        box.addView(hsv);

        box.addView(Ui.section(this, "上次结果"));
        LinearLayout lastCard = Ui.card(this, Ui.CARD, Ui.LINE);
        String last = getSharedPreferences("last", Context.MODE_PRIVATE).getString("text", "");
        TextView lastView = Ui.text(this, last.isEmpty() ? "还没有识别记录" : last, 12f,
                last.isEmpty() ? Ui.SUB : Ui.TEXT, true, false);
        lastView.setTextIsSelectable(true);
        lastCard.addView(lastView);
        box.addView(lastCard);

        box.addView(Ui.section(this, "怎么用"));
        LinearLayout guide = Ui.card(this, Ui.CARD, Ui.LINE);
        String[] steps = new String[]{
                "1. 截图后在相册/截图预览里点「分享」，选「截图取字」。",
                "2. 应用会自动识别并停在结果页，可直接编辑、复制、再分享。",
                "3. 微信聊天里的图片：长按 → 分享，同样能进来（content:// 也能读）。",
                "4. 首次用 OCR.space 的 HELLOWORLD 就能跑；量大或要更准，去设置里填自己的 key 或腾讯云密钥。"
        };
        for (String s : steps) {
            TextView tv = Ui.text(this, s, 12f, Ui.TEXT, false, false);
            tv.setPadding(0, Ui.dp(this, 6), 0, 0);
            guide.addView(tv);
        }
        box.addView(guide);

        setContentView(root);
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
