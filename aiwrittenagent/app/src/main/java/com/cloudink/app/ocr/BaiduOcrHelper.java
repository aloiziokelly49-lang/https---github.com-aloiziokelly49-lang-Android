package com.cloudink.app.ocr;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import com.cloudink.app.asr.BaiduApiErrors;
import com.cloudink.app.asr.BaiduTokenProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 百度 OCR（含手写识别），使用云墨app02 专用密钥（见 BaiduOcrConfig）。
 */
public class BaiduOcrHelper {

    private static final String TAG = "BaiduOcrHelper";
    private static final String HANDWRITING_URL =
        "https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting";
    private static final String GENERAL_URL =
        "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";

    public interface OcrCallback {
        void onSuccess(String text);
        void onError(String error);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    public void recognizeHandwriting(Bitmap bitmap, OcrCallback callback) {
        recognize(bitmap, true, callback);
    }

    public void recognizeGeneral(Bitmap bitmap, OcrCallback callback) {
        recognize(bitmap, false, callback);
    }

    private void recognize(Bitmap bitmap, boolean handwriting, OcrCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (!BaiduOcrConfig.isConfigured()) {
                    callback.onError("未配置百度 OCR：请在 local.properties 填写 baidu.ocr.api.key 与 secret.key");
                    return;
                }
                if (bitmap == null || bitmap.isRecycled()) {
                    callback.onError("图片无效");
                    return;
                }
                byte[] jpeg = bitmapToJpeg(bitmap, 92);
                String token = BaiduTokenProvider.getOcrAccessToken();
                String url = (handwriting ? HANDWRITING_URL : GENERAL_URL) + "?access_token=" + token;

                String imageB64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);

                //百度，开启自动倾斜校正
                String body = "image=" + java.net.URLEncoder.encode(imageB64, "UTF-8")
                    + "&detect_direction=true"
                    + "&probability=false";

                Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, MediaType.parse("application/x-www-form-urlencoded")))
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError("OCR 请求失败: HTTP " + response.code());
                        return;
                    }
                    String raw = response.body().string();
                    JSONObject json = new JSONObject(raw);
                    if (json.has("error_code")) {
                        int code = json.optInt("error_code");
                        String msg = json.optString("error_msg", "未知错误");
                        callback.onError(BaiduApiErrors.formatOcrError(code, msg));
                        return;
                    }
                    JSONArray words = json.optJSONArray("words_result");
                    if (words == null || words.length() == 0) {
                        callback.onError("未识别到文字");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < words.length(); i++) {
                        JSONObject line = words.getJSONObject(i);
                        String w = line.optString("words", "").trim();
                        if (!w.isEmpty()) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(w);
                        }
                    }
                    if (sb.length() == 0) {
                        callback.onError("未识别到文字");
                    } else {
                        callback.onSuccess(sb.toString());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "OCR error", e);
                callback.onError("OCR 异常: " + e.getMessage());
            }
        });
    }

    private static byte[] bitmapToJpeg(Bitmap bitmap, int quality) {
        int max = 2048;
        Bitmap work = bitmap;
        boolean recycle = false;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int edge = Math.max(w, h);
        if (edge > max) {
            float s = (float) max / edge;
            work = Bitmap.createScaledBitmap(bitmap,
                Math.round(w * s), Math.round(h * s), true);
            recycle = true;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        work.compress(Bitmap.CompressFormat.JPEG, quality, out);
        if (recycle && work != bitmap) work.recycle();
        return out.toByteArray();
    }
}
