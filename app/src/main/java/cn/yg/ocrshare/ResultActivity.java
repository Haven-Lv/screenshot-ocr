package cn.yg.ocrshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 结果页。布局用「根 LinearLayout + 两栏权重」而不是外层 ScrollView 套内层 ScrollView：
 * 后者在 Android 上内层根本收不到滑动（事件被外层拦截），正文一长就看不全。
 */
public class ResultActivity extends Activity {
    private static final String PREFS = "last";

    private Uri imageUri;
    private EditText output;
    private TextView status;
    private FlowSelectView flow;
    private TextView selHint;
    private Cfg cfg;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Cfg.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 12));

        // ---- 头部
        LinearLayout hero = Ui.card(this, Ui.CARD, Ui.LINE);
        hero.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView kicker = Ui.text(this, "SCREENSHOT OCR", 10f, Ui.ACCENT, true, false);
        kicker.setLetterSpacing(0.22f);
        hero.addView(kicker);
        hero.addView(Ui.text(this, "截图取字", 22f, Ui.TEXT, false, true));
        status = Ui.text(this, "准备中…", 11f, Ui.SUB, true, false);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        hero.addView(status);
        root.addView(hero);

        // ---- 上半：识别结果（可编辑，内部可滚）
        root.addView(Ui.section(this, "识别结果（可上下滑动 / 可编辑）"));
        LinearLayout outCard = Ui.card(this, Ui.CARD, Ui.LINE);
        outCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ScrollView outScroll = new ScrollView(this);
        outScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        outScroll.setFillViewport(true);
        output = new EditText(this);
        output.setBackgroundColor(Ui.CARD);
        output.setTextColor(Ui.TEXT);
        output.setTextSize(13f);
        output.setGravity(Gravity.TOP | Gravity.START);
        output.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        outScroll.addView(output);
        outCard.addView(outScroll);
        root.addView(outCard);

        // ---- 下半：分词（内部可滚）
        root.addView(Ui.section(this, "分词选词 · 按住滑动"));
        LinearLayout pickCard = Ui.card(this, Ui.CARD, Ui.LINE);
        pickCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        selHint = Ui.text(this, "按住词块滑动 = 区间选择，松手即复制；点单个词块只复制该词。",
                10.5f, Ui.SUB, true, false);
        pickCard.addView(selHint);

        flow = new FlowSelectView(this);
        flow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        flow.setPickListener((first, last, drag) -> {
            String picked = flow.textOf(first, last);
            if (picked.isEmpty()) {
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ocr-pick", picked));
            }
            String preview = picked.length() > 16 ? picked.substring(0, 16) + "…" : picked;
            selHint.setTextColor(Ui.OK);
            selHint.setText("已复制 " + picked.length() + " 字：" + preview.replace("\n", " "));
            toast("已复制：" + preview.replace("\n", " "));
        });
        ScrollView flowScroll = new ScrollView(this);
        flowScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        flowScroll.addView(flow);
        pickCard.addView(flowScroll);
        root.addView(pickCard);

        // ---- 操作区（贴底）
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView copy = Ui.button(this, "复制", true);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ocr", output.getText()));
                toast("已复制 " + output.getText().length() + " 字");
            }
        });
        TextView share = Ui.button(this, "分享文字", false);
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
            startActivity(Intent.createChooser(i, "分享识别结果"));
        });
        TextView retry = Ui.button(this, "重试", false);
        retry.setOnClickListener(v -> {
            output.setText("");
            runOcr();
        });
        TextView reseg = Ui.button(this, "重新分词", false);
        reseg.setOnClickListener(v -> {
            flow.setTokens(WordSeg.split(output.getText().toString()));
            selHint.setTextColor(Ui.SUB);
            selHint.setText("已重新分词 · " + flow.size() + " 个词块");
        });
        TextView st = Ui.button(this, "设置", false);
        st.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        actions.addView(copy);
        actions.addView(share);
        actions.addView(retry);
        actions.addView(reseg);
        actions.addView(st);

        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionScroll.addView(actions);
        root.addView(actionScroll);

        setContentView(root);

        imageUri = pickImage(getIntent());
        if (imageUri == null) {
            status.setTextColor(Ui.ERR);
            status.setText("没有拿到图片");
            return;
        }
        runOcr();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri next = pickImage(intent);
        if (next != null) {
            imageUri = next;
            output.setText("");
            runOcr();
        }
    }

    private Uri pickImage(Intent intent) {
        if (intent == null) {
            return null;
        }
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) {
            return (Uri) stream;
        }
        String path = intent.getStringExtra("image_path");
        if (path != null) {
            File f = new File(path);
            if (f.exists()) {
                return Uri.fromFile(f);
            }
        }
        ClipData clip = intent.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            return clip.getItemAt(0).getUri();
        }
        return intent.getData();
    }

    private void runOcr() {
        final Uri uri = imageUri;
        status.setTextColor(Ui.SUB);
        status.setText("识别中 · " + cfg.providerName() + " · 长边 " + cfg.maxEdge + "px");
        pool.execute(() -> {
            try {
                Bitmap bmp = Img.load(this, uri, cfg.maxEdge);
                byte[] jpeg = Img.toJpeg(bmp, cfg.quality);
                if (jpeg.length > 950_000) {
                    jpeg = Img.toJpeg(bmp, 60);
                }
                final String text = OcrClient.recognize(cfg, jpeg, cfg.lang);
                final int kb = jpeg.length / 1024;
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("text", text)
                        .putLong("at", System.currentTimeMillis())
                        .apply();
                runOnUiThread(() -> {
                    output.setText(text);
                    flow.setTokens(WordSeg.split(text));
                    selHint.setTextColor(Ui.SUB);
                    selHint.setText("已分词 · " + flow.size() + " 个词块 · " + text.length() + " 字");
                    status.setTextColor(Ui.OK);
                    status.setText("完成 · " + text.length() + " 字 · 上传 " + kb + " KB · "
                            + cfg.providerName());
                });
            } catch (Throwable t) {
                String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                runOnUiThread(() -> {
                    status.setTextColor(Ui.ERR);
                    status.setText("失败 · " + msg);
                });
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }
}
