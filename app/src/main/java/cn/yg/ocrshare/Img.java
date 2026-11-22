package cn.yg.ocrshare;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class Img {
    private Img() {
    }

    static Bitmap load(Context ctx, Uri uri, int maxEdge) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new Exception("无法读取图片");
        }
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / (sample * 2) >= maxEdge) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) {
            throw new Exception("解码失败");
        }
        bmp = rotateByExif(ctx, uri, bmp);
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int longSide = Math.max(w, h);
        if (longSide > maxEdge) {
            float scale = maxEdge / (float) longSide;
            bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * scale)),
                    Math.max(1, Math.round(h * scale)), true);
        }
        return bmp;
    }

    private static Bitmap rotateByExif(Context ctx, Uri uri, Bitmap bmp) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            int deg = 0;
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) {
                deg = 90;
            } else if (o == ExifInterface.ORIENTATION_ROTATE_180) {
                deg = 180;
            } else if (o == ExifInterface.ORIENTATION_ROTATE_270) {
                deg = 270;
            }
            if (deg != 0) {
                Matrix m = new Matrix();
                m.postRotate(deg);
                return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
        } catch (Throwable ignored) {
        }
        return bmp;
    }

    static byte[] toJpeg(Bitmap bmp, int quality) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos);
        return bos.toByteArray();
    }

    static String base64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }
}
