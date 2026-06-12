package com.cloudink.app.asr;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 百度语音识别API帮助类
 * 
 * 使用说明：
 * 1. 在百度AI开放平台注册账号：https://ai.baidu.com/
 * 2. 创建应用获取 API Key 和 Secret Key
 * 3. 将下面的 API_KEY 和 SECRET_KEY 替换为你的密钥
 * 4. 在 build.gradle 中添加依赖：implementation 'com.squareup.okhttp3:okhttp:4.12.0'
 * 
 * 免费额度：每天50000次调用
 */
public class BaiduAsrHelper {
    private static final String TAG = "BaiduAsrHelper";
    
    private static final String ASR_URL = "https://vop.baidu.com/server_api";

    private final OkHttpClient client;
    
    public BaiduAsrHelper() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 识别回调接口
     */
    public interface AsrCallback {
        void onSuccess(String result);
        void onError(String error);
    }
    
    /**
     * 识别音频文件
     * 
     * @param audioFilePath 音频文件路径（支持 pcm/wav/amr/m4a 格式）
     * @param callback 识别结果回调
     */
    public void recognizeAudioFile(String audioFilePath, AsrCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (!BaiduAsrConfig.isConfigured()) {
                    callback.onError("未配置百度语音：请在 local.properties 填写 baidu.asr.api.key 与 secret.key");
                    return;
                }
                
                // 1. 读取音频文件
                File audioFile = new File(audioFilePath);
                if (!audioFile.exists()) {
                    callback.onError("音频文件不存在");
                    return;
                }
                
                if (audioFile.length() > 20 * 1024 * 1024) {
                    callback.onError("音频文件过大（超过 20MB），请换较短录音");
                    return;
                }

                byte[] audioData;
                String format;
                String lower = audioFilePath.toLowerCase();
                if (lower.endsWith(".pcm")) {
                    audioData = AudioPcmConverter.toPcm16Mono16k(audioFile);
                    format = "pcm";
                } else if (lower.endsWith(".wav")) {
                    audioData = AudioPcmConverter.toPcm16Mono16k(audioFile);
                    format = "pcm";
                } else if (lower.endsWith(".amr")) {
                    audioData = Files.readAllBytes(audioFile.toPath());
                    format = "amr";
                } else {
                    audioData = AudioPcmConverter.toPcm16Mono16k(audioFile);
                    format = "pcm";
                }
                Log.d(TAG, "上传 PCM 大小: " + audioData.length + " format=" + format);
                
                String accessToken = BaiduTokenProvider.getAccessToken();
                String result = callAsrApi(audioData, format, accessToken);
                
                // 5. 解析结果
                JSONObject json = new JSONObject(result);
                int errNo = json.optInt("err_no", -1);
                
                if (errNo == 0 && json.has("result")) {
                    JSONArray results = json.getJSONArray("result");
                    if (results.length() > 0) {
                        String text = results.getString(0);
                        Log.d(TAG, "识别成功: " + text);
                        callback.onSuccess(text);
                    } else {
                        callback.onError("未识别到语音内容");
                    }
                } else {
                    String errMsg = json.optString("err_msg", "未知错误");
                    Log.e(TAG, "识别失败: err_no=" + errNo + ", err_msg=" + errMsg);
                    callback.onError(BaiduApiErrors.formatAsrError(errNo, errMsg));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "识别异常", e);
                callback.onError("识别异常: " + e.getMessage());
            }
        });
    }
    
    /**
     * 调用百度语音识别API
     */
    private String callAsrApi(byte[] audioData, String format, String accessToken) throws IOException {
        // 构建请求参数
        JSONObject params = new JSONObject();
        try {
            params.put("format", format);
            params.put("rate", 16000); // 采样率，支持 8000 或 16000
            params.put("channel", 1);  // 声道数，仅支持单声道
            params.put("cuid", "android_cloudink_app");
            params.put("token", accessToken);
            params.put("speech", Base64.encodeToString(audioData, Base64.NO_WRAP));
            params.put("len", audioData.length);
            params.put("dev_pid", 1537); // 识别模型：1537=普通话(纯中文识别)，1737=英语，1837=粤语
        } catch (Exception e) {
            throw new IOException("构建请求参数失败: " + e.getMessage());
        }
        
        RequestBody body = RequestBody.create(
                params.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
                .url(ASR_URL)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败: HTTP " + response.code());
            }
            
            return response.body().string();
        }
    }
    
    /**
     * 检测音频格式
     */
    private String detectAudioFormat(String filePath) {
        String lowerPath = filePath.toLowerCase();
        
        if (lowerPath.endsWith(".pcm")) {
            return "pcm";
        } else if (lowerPath.endsWith(".wav")) {
            return "wav";
        } else if (lowerPath.endsWith(".amr")) {
            return "amr";
        } else if (lowerPath.endsWith(".m4a")) {
            return "m4a";
        } else if (lowerPath.endsWith(".mp3")) {
            // 百度API不直接支持mp3，需要转换
            return "wav"; // 尝试作为wav处理
        } else {
            // 默认尝试wav格式
            return "wav";
        }
    }
    
    /**
     * 清理资源
     */
    public void release() {
        // OkHttpClient会自动管理连接池，不需要手动关闭
    }
}
