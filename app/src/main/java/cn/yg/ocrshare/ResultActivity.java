package cn.yg.ocrshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 结果页：只保留「分词选词」。词块区上下可滑（竖向拖动由词块自己驱动外层滚动），
 * 轻点复制单词，横向拖动区间选择；顶部不再有可编辑文本框。
 */
public class ResultActivity extends Activity {
    private static final String PREFS = "last";

    private Uri imageUri;
    private String lastText = "";
    private TextView status;
    private TextView selHint;
    private FlowSelectView flow;
    private Cfg cfg;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Cfg.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 10));

        LinearLayout hero = Ui.card(this, Ui.CARD, Ui.LINE);
        hero.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView kicker = Ui.text(this, "SCREENSHOT OCR", 10f, Ui.ACCENT, true, false);
        kicker.setLetterSpacing(0.22f);
        hero.addView(kicker);
        hero.addView(Ui.text(this, "截图取字", 21f, Ui.TEXT, false, true));
        status = Ui.text(this, "准备中…", 11f, Ui.SUB, true, false);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        hero.addView(status);
        root.addView(hero);

        root.addView(Ui.section(this, "分词选词"));
        LinearLayout pickCard = Ui.card(this, Ui.CARD, Ui.LINE);
        pickCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        pickCard.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));

        selHint = Ui.text(this, "上下滑动浏览 · 轻点词块复制该词 · 横向滑动区间选择",
                10.5f, Ui.SUB, true, false);
        selHint.setLineSpacing(Ui.dp(this, 3), 1f);
        pickCard.addView(selHint);

        flow = new FlowSelectView(this);
        // 词块四周留白：手指落在留白/间隙里就是纯滑动，不会误触复制
        flow.setPadding(Ui.dp(this, 2), Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 26));
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
        flowScroll.setClipToPadding(false);
        flowScroll.addView(flow);
        pickCard.addView(flowScroll);
        root.addView(pickCard);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView copyAll = Ui.button(this, "复制全部", true);
        copyAll.setOnClickListener(v -> {
            if (lastText.isEmpty()) {
                toast("还没有结果");
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ocr-all", lastText));
                toast("已复制全文 " + lastText.length() + " 字");
            }
        });
        TextView share = Ui.button(this, "分享", false);
        share.setOnClickListener(v -> {
            if (lastText.isEmpty()) {
                return;
            }
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, lastText);
            startActivity(Intent.createChooser(i, "分享识别结果"));
        });
        TextView retry = Ui.button(this, "重新识别", false);
        retry.setOnClickListener(v -> runOcr());
        TextView st = Ui.button(this, "设置", false);
        st.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        actions.addView(copyAll);
        actions.addView(share);
        actions.addView(retry);
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
            lastText = "";
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
        if (uri == null) {
            status.setTextColor(Ui.ERR);
            status.setText("没有拿到图片");
            return;
        }
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
                    lastText = text;
                    flow.setTokens(WordSeg.split(text));
                    selHint.setTextColor(Ui.SUB);
                    selHint.setText("共 " + text.length() + " 字 / " + flow.size()
                            + " 词块 · 上下滑动浏览 · 轻点复制 · 横向滑动区间选择");
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
