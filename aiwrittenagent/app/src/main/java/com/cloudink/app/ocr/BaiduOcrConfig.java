package com.cloudink.app.ocr;

import com.cloudink.app.BuildConfig;

/**
 * 百度文字识别 / 手写 OCR 配置（云墨app02，与语音识别密钥分开）。
 */
public final class BaiduOcrConfig {

    private BaiduOcrConfig() {}

    public static String getApiKey() {
        return BuildConfig.BAIDU_OCR_API_KEY != null ? BuildConfig.BAIDU_OCR_API_KEY : "";
    }

    public static String getSecretKey() {
        return BuildConfig.BAIDU_OCR_SECRET_KEY != null ? BuildConfig.BAIDU_OCR_SECRET_KEY : "";
    }

    public static String getAppId() {
        return BuildConfig.BAIDU_OCR_APP_ID != null ? BuildConfig.BAIDU_OCR_APP_ID : "";
    }

    public static boolean isConfigured() {
        String key = getApiKey();
        String secret = getSecretKey();
        return key != null && !key.isEmpty() && !key.startsWith("YOUR_")
            && secret != null && !secret.isEmpty() && !secret.startsWith("YOUR_");
    }
}
