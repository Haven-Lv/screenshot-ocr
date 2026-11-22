package cn.yg.ocrshare;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 两个云端实现：OCR.space（免签名）与腾讯云 通用印刷体（TC3 签名）。 */
final class OcrClient {

    private OcrClient() {
    }

    static String recognize(Cfg cfg, byte[] jpeg, String lang) throws Exception {
        if (cfg.provider == Cfg.PROVIDER_TENCENT) {
            return tencent(cfg, jpeg);
        }
        return ocrSpace(cfg, jpeg, lang);
    }

    // ------------------------------------------------------------ OCR.space

    static String ocrSpace(Cfg cfg, byte[] jpeg, String lang) throws Exception {
        String boundary = "----OcrShare" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "apikey", cfg.ocrSpaceKey);
        field(body, boundary, "language", lang);
        field(body, boundary, "OCREngine", "2");
        field(body, boundary, "isOverlayRequired", "false");
        field(body, boundary, "scale", "true");
        field(body, boundary, "detectOrientation", "true");
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"shot.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n");
        body.write(jpeg);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.ocr.space/parse/image").openConnection();
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("apikey", cfg.ocrSpaceKey);
        try (OutputStream os = conn.getOutputStream()) {
            body.writeTo(os);
        }
        int code = conn.getResponseCode();
        String resp = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new Exception("OCR.space HTTP " + code + " · " + clip(resp));
        }
        JSONObject o = new JSONObject(resp);
        if (o.optBoolean("IsErroredOnProcessing")) {
            throw new Exception("OCR.space 失败：" + clip(String.valueOf(o.opt("ErrorMessage"))));
        }
        JSONArray arr = o.optJSONArray("ParsedResults");
        if (arr == null || arr.length() == 0) {
            throw new Exception("OCR.space 返回空结果");
        }
        String text = arr.getJSONObject(0).optString("ParsedText", "");
        text = stripDemoNotice(text);
        if (text.trim().isEmpty()) {
            throw new Exception("未识别到文字（图片太糊或语言不匹配）");
        }
        return text;
    }

    /** HELLOWORLD 演示 key 会在结果里塞一段英文提示，去掉它，免得混进正文。 */
    private static String stripDemoNotice(String text) {
        String s = text;
        int i = s.indexOf("[Note: the 'helloworld'");
        if (i >= 0) {
            int j = s.indexOf(']', i);
            if (j > i) {
                s = s.substring(0, i) + s.substring(j + 1);
            }
        }
        while (s.startsWith("\n") || s.startsWith("\r")) {
            s = s.substring(1);
        }
        return s.trim().isEmpty() ? text : s;
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
        write(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------ 腾讯云

    static String tencent(Cfg cfg, byte[] jpeg) throws Exception {
        final String host = "ocr.tencentcloudapi.com";
        final String service = "ocr";
        final String action = "GeneralBasicOCR";
        final String version = "2018-11-19";
        final String region = cfg.tencentRegion == null || cfg.tencentRegion.isEmpty()
                ? "ap-guangzhou" : cfg.tencentRegion;

        String bodyStr = "{\"ImageBase64\":\"" + Img.base64(jpeg) + "\",\"LanguageType\":\"zh\"}";
        byte[] bodyBytes = bodyStr.getBytes(StandardCharsets.UTF_8);

        long ts = System.currentTimeMillis() / 1000L;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = fmt.format(new Date(ts * 1000L));

        String signedHeaders = "content-type;host";
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + host + "\n";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n"
                + sha256Hex(bodyBytes);
        String scope = date + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + ts + "\n" + scope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        byte[] kDate = hmac(("TC3" + cfg.tencentKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kService = hmac(kDate, service);
        byte[] kSigning = hmac(kService, "tc3_request");
        String signature = hex(hmac(kSigning, stringToSign));

        String authorization = "TC3-HMAC-SHA256 Credential=" + cfg.tencentId + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpURLConnection conn = (HttpURLConnection) new URL("https://" + host).openConnection();
        conn.setDoOutput(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", authorization);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Host", host);
        conn.setRequestProperty("X-TC-Action", action);
        conn.setRequestProperty("X-TC-Timestamp", String.valueOf(ts));
        conn.setRequestProperty("X-TC-Version", version);
        conn.setRequestProperty("X-TC-Region", region);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
        }
        int code = conn.getResponseCode();
        String resp = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code >= 400) {
            throw new Exception("腾讯云 HTTP " + code + " · " + clip(resp));
        }
        JSONObject root = new JSONObject(resp);
        JSONObject r = root.optJSONObject("Response");
        if (r == null) {
            throw new Exception("腾讯云返回异常：" + clip(resp));
        }
        JSONObject err = r.optJSONObject("Error");
        if (err != null) {
            String c = err.optString("Code");
            String msg = err.optString("Message");
            if (c.contains("RequestLimitExceeded")) {
                throw new Exception("腾讯云限流/额度用尽（" + c + "）：" + msg);
            }
            if (c.contains("AuthFailure") || c.contains("Signature")) {
                throw new Exception("腾讯云鉴权失败（" + c + "）：检查 SecretId/SecretKey 与设备时间");
            }
            throw new Exception("腾讯云 " + c + "：" + msg);
        }
        JSONArray det = r.optJSONArray("TextDetections");
        if (det == null || det.length() == 0) {
            throw new Exception("未识别到文字（图片太糊或语言不匹配）");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < det.length(); i++) {
            JSONObject item = det.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String t = item.optString("DetectedText", "");
            if (!t.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t);
            }
        }
        String text = sb.toString();
        if (text.trim().isEmpty()) {
            throw new Exception("未识别到文字");
        }
        return text;
    }

    // ------------------------------------------------------------ 工具

    private static String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            bos.write(buf, 0, r);
        }
        in.close();
        return bos.toString("UTF-8");
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return hex(md.digest(data));
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String clip(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
