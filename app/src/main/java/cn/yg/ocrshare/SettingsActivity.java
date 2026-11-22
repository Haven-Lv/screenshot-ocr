package cn.yg.ocrshare;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private Cfg cfg;
    private TextView providerView;
    private TextView btnSpace;
    private TextView btnTc;
    private TextView btnAcc;
    private EditText ocrKey;
    private EditText tcId;
    private EditText tcKey;
    private EditText tcRegion;
    private EditText lang;
    private EditText maxEdge;
    private EditText quality;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Cfg.load(this);

        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 26), Ui.dp(this, 16), Ui.dp(this, 28));
        root.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView back = Ui.button(this, "← 返回", false);
        back.setOnClickListener(v -> finish());
        box.addView(back);

        box.addView(Ui.section(this, "识别服务"));
        LinearLayout pCard = Ui.card(this, Ui.CARD, Ui.LINE);
        providerView = Ui.text(this, "", 14f, Ui.ACCENT, false, true);
        pCard.addView(providerView);
        TextView hint = Ui.text(this, "OCR.space：填 apikey，免费 500 次/天/IP，中文一般。\n"
                + "腾讯云·通用 GeneralBasicOCR：1000 次/月，日常截图够用。\n"
                + "腾讯云·高精度 GeneralAccurateOCR：同一份免费额度，小字/密排更准，代价是慢一点。", 11f, Ui.SUB, false, false);
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        pCard.addView(hint);
        LinearLayout pRow = new LinearLayout(this);
        pRow.setOrientation(LinearLayout.HORIZONTAL);
        btnSpace = Ui.button(this, "OCR.space", false);
        btnSpace.setOnClickListener(v -> {
            cfg.provider = Cfg.PROVIDER_OCRSPACE;
            renderProvider();
        });
        btnTc = Ui.button(this, "腾讯云·通用", false);
        btnTc.setOnClickListener(v -> {
            cfg.provider = Cfg.PROVIDER_TENCENT;
            renderProvider();
        });
        btnAcc = Ui.button(this, "腾讯云·高精度", false);
        btnAcc.setOnClickListener(v -> {
            cfg.provider = Cfg.PROVIDER_TENCENT_ACC;
            renderProvider();
        });
        pRow.addView(btnSpace);
        pRow.addView(btnTc);
        pRow.addView(btnAcc);
        pCard.addView(pRow);
        box.addView(pCard);

        box.addView(Ui.section(this, "OCR.space"));
        LinearLayout sCard = Ui.card(this, Ui.CARD, Ui.LINE);
        ocrKey = field(sCard, "apikey（HELLOWORLD 可直接试用）", cfg.ocrSpaceKey, false);
        sCard.addView(Ui.text(this, "申请：https://ocr.space/ocrapi/freekey", 10.5f, Ui.SUB, true, false));
        box.addView(sCard);

        box.addView(Ui.section(this, "腾讯云"));
        LinearLayout tCard = Ui.card(this, Ui.CARD, Ui.LINE);
        tcId = field(tCard, "SecretId", cfg.tencentId, false);
        tcKey = field(tCard, "SecretKey", cfg.tencentKey, true);
        tcRegion = field(tCard, "地域（默认 ap-guangzhou）", cfg.tencentRegion, false);
        box.addView(tCard);

        box.addView(Ui.section(this, "通用"));
        LinearLayout gCard = Ui.card(this, Ui.CARD, Ui.LINE);
        lang = field(gCard, "语言（chs / eng）", cfg.lang, false);
        maxEdge = field(gCard, "长边压缩像素（默认 2000）", String.valueOf(cfg.maxEdge), false);
        quality = field(gCard, "JPEG 质量（默认 80）", String.valueOf(cfg.quality), false);
        box.addView(gCard);

        TextView save = Ui.button(this, "保存", true);
        save.setOnClickListener(v -> {
            cfg.ocrSpaceKey = ocrKey.getText().toString().trim();
            cfg.tencentId = tcId.getText().toString().trim();
            cfg.tencentKey = tcKey.getText().toString().trim();
            cfg.tencentRegion = tcRegion.getText().toString().trim();
            if (cfg.tencentRegion.isEmpty()) {
                cfg.tencentRegion = "ap-guangzhou";
            }
            String l = lang.getText().toString().trim();
            cfg.lang = l.isEmpty() ? "chs" : l;
            cfg.maxEdge = clampInt(maxEdge.getText().toString(), 2000, 600, 4000);
            cfg.quality = clampInt(quality.getText().toString(), 80, 40, 95);
            cfg.save(this);
            Toast.makeText(this, "已保存 · " + cfg.providerName(), Toast.LENGTH_SHORT).show();
            finish();
        });
        box.addView(save);

        setContentView(root);
        renderProvider();
    }

    private void renderProvider() {
        providerView.setText("当前：" + cfg.providerName());
        providerView.setTextColor(Ui.ACCENT);
        // 选中哪个，哪个高亮并打勾；其余保持描边
        boolean space = cfg.provider == Cfg.PROVIDER_OCRSPACE;
        boolean tc = cfg.provider == Cfg.PROVIDER_TENCENT;
        boolean acc = cfg.provider == Cfg.PROVIDER_TENCENT_ACC;
        Ui.setButtonActive(this, btnSpace, space);
        Ui.setButtonActive(this, btnTc, tc);
        Ui.setButtonActive(this, btnAcc, acc);
        if (btnSpace != null) {
            btnSpace.setText(space ? "✓ OCR.space" : "OCR.space");
        }
        if (btnTc != null) {
            btnTc.setText(tc ? "✓ 腾讯云·通用" : "腾讯云·通用");
        }
        if (btnAcc != null) {
            btnAcc.setText(acc ? "✓ 腾讯云·高精度" : "腾讯云·高精度");
        }
    }

    private EditText field(LinearLayout parent, String label, String value, boolean secret) {
        TextView l = Ui.text(this, label, 11f, Ui.SUB, false, false);
        l.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 2));
        parent.addView(l);
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setTextSize(13f);
        e.setTextColor(Ui.TEXT);
        e.setBackground(Ui.round(this, Ui.CARD_ALT, Ui.LINE, 10f));
        e.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        e.setSingleLine(true);
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        parent.addView(e);
        return e;
    }

    private static int clampInt(String s, int def, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return def;
        }
    }
}
