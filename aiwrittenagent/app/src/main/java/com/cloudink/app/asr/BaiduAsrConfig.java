package com.cloudink.app.asr;

import com.cloudink.app.BuildConfig;

/**
 * 百度语音识别配置（从 local.properties 注入 BuildConfig，勿把密钥写进 Java 源码）。
 */
public final class BaiduAsrConfig {

    private BaiduAsrConfig() {}

    public static String getApiKey() {
        return BuildConfig.BAIDU_ASR_API_KEY != null ? BuildConfig.BAIDU_ASR_API_KEY : "";
    }

    public static String getSecretKey() {
        return BuildConfig.BAIDU_ASR_SECRET_KEY != null ? BuildConfig.BAIDU_ASR_SECRET_KEY : "";
    }

    public static String getAppId() {
        return BuildConfig.BAIDU_ASR_APP_ID != null ? BuildConfig.BAIDU_ASR_APP_ID : "";
    }

    public static boolean isConfigured() {
        String key = getApiKey();
        String secret = getSecretKey();
        return key != null && !key.isEmpty() && !key.startsWith("YOUR_")
            && secret != null && !secret.isEmpty() && !secret.startsWith("YOUR_");
    }
}
