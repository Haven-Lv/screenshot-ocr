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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 结果页：一个 TextView + Span 的分词视图（原生滚动）。
 * 两种进入方式：分享/选图（跑 OCR），或首页历史（直接显示文本）。
 *
 * 代码审查后的关键修正：
 * - onResume 重读 Cfg（从设置页回来能立刻用新 provider）
 * - 请求序号 gen：旋转/连分享时旧任务的结果不会覆盖新图
 * - 压缩改为循环降质，直到 ≤999KB；bitmap 用完 recycle
 */
public class ResultActivity extends Activity {
    public static final String EXTRA_TEXT = "text";

    private Uri imageUri;
    private String lastText = "";
    private TextView status;
    private TextView selHint;
    private TokenTextView tokenView;
    private Cfg cfg;
    private final AtomicInteger gen = new AtomicInteger();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = Cfg.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 10));
        Ui.applyInsets(root);

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
        LinearLayout card = Ui.card(this, Ui.CARD, Ui.LINE);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));

        selHint = Ui.text(this, "上下滑动浏览（原生滚动）· 轻点复制单词 · 横向拖过复制整段",
                10.5f, Ui.SUB, true, false);
        selHint.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(selHint);

        tokenView = new TokenTextView(this);
        tokenView.setPadding(Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 2), Ui.dp(this, 24));
        tokenView.setPickListener(picked -> {
            if (picked.trim().isEmpty()) {
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
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setClipToPadding(false);
        scroll.addView(tokenView);
        card.addView(scroll);
        root.addView(card);

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
        retry.setOnClickListener(v -> {
            if (imageUri == null) {
                toast("这是历史记录，没有原图");
                return;
            }
            runOcr();
        });
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

        String histText = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_TEXT);
        imageUri = pickImage(getIntent());
        if (histText != null) {
            showText(histText, "历史记录");
            return;
        }
        if (imageUri == null) {
            status.setTextColor(Ui.ERR);
            status.setText("没有拿到图片");
            return;
        }
        runOcr();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页回来时要拿到新的 provider / 密钥
        cfg = Cfg.load(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri next = pickImage(intent);
        if (next != null) {
            gen.incrementAndGet(); // 作废在途请求
            imageUri = next;
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

    private void showText(String text, String source) {
        lastText = text;
        tokenView.setContent(text);
        selHint.setTextColor(Ui.SUB);
        selHint.setText("共 " + text.length() + " 字 / " + tokenView.tokenCount()
                + " 词元 · 上下滑动 · 轻点复制 · 横向拖过复制整段");
        status.setTextColor(Ui.OK);
        status.setText(source + " · " + text.length() + " 字");
    }

    private byte[] compress(Bitmap bmp) {
        int[] qualities = new int[]{cfg.quality, 60, 45, 35};
        byte[] jpeg = Img.toJpeg(bmp, qualities[0]);
        for (int i = 1; i < qualities.length && jpeg.length > 999_000; i++) {
            jpeg = Img.toJpeg(bmp, qualities[i]);
        }
        return jpeg;
    }

    private void runOcr() {
        final Uri uri = imageUri;
        if (uri == null) {
            return;
        }
        final int my = gen.incrementAndGet();
        status.setTextColor(Ui.SUB);
        status.setText("识别中 · " + cfg.providerName() + " · 长边 " + cfg.maxEdge + "px");
        pool.execute(() -> {
            Bitmap bmp = null;
            try {
                bmp = Img.load(this, uri, cfg.maxEdge);
                byte[] jpeg = compress(bmp);
                bmp.recycle();
                bmp = null;
                if (my != gen.get()) {
                    return;
                }
                final String text = OcrClient.recognize(cfg, jpeg, cfg.lang);
                if (my != gen.get()) {
                    return;
                }
                History.add(this, text);
                final int kb = jpeg.length / 1024;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || my != gen.get()) {
                        return;
                    }
                    showText(text, "完成");
                    status.setText("完成 · " + text.length() + " 字 · 上传 " + kb + " KB · "
                            + cfg.providerName());
                });
            } catch (Throwable t) {
                if (bmp != null) {
                    bmp.recycle();
                }
                String msg = t.getMessage() == null ? t.toString() : t.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || my != gen.get()) {
                        return;
                    }
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
        gen.incrementAndGet();
        pool.shutdownNow();
        super.onDestroy();
    }
}
