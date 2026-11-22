package cn.yg.ocrshare;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地配置：provider、密钥、语言、压缩尺寸。 */
final class Cfg {
    static final int PROVIDER_OCRSPACE = 0;
    static final int PROVIDER_TENCENT = 1;
    /** 腾讯云 通用文字识别（高精度版）GeneralAccurateOCR，对截图/小字更稳 */
    static final int PROVIDER_TENCENT_ACC = 2;

    int provider = PROVIDER_OCRSPACE;
    String ocrSpaceKey = "HELLOWORLD";
    String tencentId = "";
    String tencentKey = "";
    String tencentRegion = "ap-guangzhou";
    String lang = "chs";
    int maxEdge = 2000;
    int quality = 80;

    static Cfg load(Context c) {
        Cfg cfg = new Cfg();
        SharedPreferences sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        cfg.provider = sp.getInt("provider", cfg.provider);
        cfg.ocrSpaceKey = sp.getString("ocrKey", cfg.ocrSpaceKey);
        cfg.tencentId = sp.getString("tcId", cfg.tencentId);
        cfg.tencentKey = sp.getString("tcKey", cfg.tencentKey);
        cfg.tencentRegion = sp.getString("tcRegion", cfg.tencentRegion);
        cfg.lang = sp.getString("lang", cfg.lang);
        cfg.maxEdge = sp.getInt("maxEdge", cfg.maxEdge);
        cfg.quality = sp.getInt("quality", cfg.quality);
        return cfg;
    }

    void save(Context c) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
                .putInt("provider", provider)
                .putString("ocrKey", ocrSpaceKey)
                .putString("tcId", tencentId)
                .putString("tcKey", tencentKey)
                .putString("tcRegion", tencentRegion)
                .putString("lang", lang)
                .putInt("maxEdge", maxEdge)
                .putInt("quality", quality)
                .apply();
    }

    String providerName() {
        if (provider == PROVIDER_TENCENT) {
            return "腾讯云·通用版";
        }
        if (provider == PROVIDER_TENCENT_ACC) {
            return "腾讯云·高精度";
        }
        return "OCR.space";
    }

    boolean ready() {
        if (provider != PROVIDER_OCRSPACE) {
            return !tencentId.trim().isEmpty() && !tencentKey.trim().isEmpty();
        }
        return !ocrSpaceKey.trim().isEmpty();
    }
}
