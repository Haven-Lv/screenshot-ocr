package cn.yg.ocrshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** 分享入口：接住系统分享的图片，转给结果页后立刻退出。 */
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
        Intent next = new Intent(this, ResultActivity.class);
        if (uri != null) {
            next.setData(uri);
            next.putExtra(Intent.EXTRA_STREAM, uri);
            next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(next);
        finish();
    }
}
