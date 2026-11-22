package cn.yg.ocrshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** 分享入口：接住图片 → 转给结果页 → 立刻退出。 */
public class ShareActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent src = getIntent();
        Uri uri = null;
        if (src != null) {
            Object extra = src.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) {
                uri = (Uri) extra;
            }
            if (uri == null && src.getClipData() != null && src.getClipData().getItemCount() > 0) {
                ClipData.Item item = src.getClipData().getItemAt(0);
                uri = item.getUri();
            }
            if (uri == null) {
                uri = src.getData();
            }
        }
        if (uri == null) {
            Toast.makeText(this, "没有拿到图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent next = new Intent(this, ResultActivity.class);
        next.setData(uri);
        next.putExtra(Intent.EXTRA_STREAM, uri);
        // CLEAR_TOP + SINGLE_TOP：连续分享只保留一个结果页，并触发 onNewIntent 换图
        next.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(next);
        finish();
    }
}
