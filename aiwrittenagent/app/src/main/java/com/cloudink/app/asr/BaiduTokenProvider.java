package com.cloudink.app.asr;

import android.util.Log;

import com.cloudink.app.ocr.BaiduOcrConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** 百度 OAuth access_token 缓存（语音 / OCR 各用一套密钥）。 */
public final class BaiduTokenProvider {

    private static final String TAG = "BaiduToken";
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private static final class TokenEntry {
        String token;
        long expireAt;
    }

    private static final ConcurrentHashMap<String, TokenEntry> CACHE = new ConcurrentHashMap<>();

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();

    private BaiduTokenProvider() {}

    /** 语音识别 token */
    public static String getAccessToken() throws IOException {
        return getAccessToken(BaiduAsrConfig.getApiKey(), BaiduAsrConfig.getSecretKey());
    }

    /** 文字识别 / 手写 OCR token */
    public static String getOcrAccessToken() throws IOException {
        return getAccessToken(BaiduOcrConfig.getApiKey(), BaiduOcrConfig.getSecretKey());
    }

    public static String getAccessToken(String apiKey, String secretKey) throws IOException {
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            throw new IOException("未配置百度 API Key / Secret Key");
        }

        TokenEntry entry = CACHE.get(apiKey);
        if (entry != null && entry.token != null && System.currentTimeMillis() < entry.expireAt) {
            return entry.token;
        }

        String url = TOKEN_URL + "?grant_type=client_credentials"
            + "&client_id=" + apiKey
            + "&client_secret=" + secretKey;

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("获取 token 失败 HTTP " + response.code());
            }
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            if (!json.has("access_token")) {
                throw new IOException("token 响应异常: " + body);
            }
            TokenEntry fresh = new TokenEntry();
            fresh.token = json.getString("access_token");
            int expiresIn = json.optInt("expires_in", 2592000);
            fresh.expireAt = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
            CACHE.put(apiKey, fresh);
            Log.i(TAG, "access token refreshed for client " + apiKey.substring(0, Math.min(6, apiKey.length())) + "…");
            return fresh.token;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("解析 token 失败: " + e.getMessage(), e);
        }
    }
}
