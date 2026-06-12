package com.cloudink.app.asr;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.cloudink.app.event.AudioTranscribeEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 封装 Android 系统内置 SpeechRecognizer, 实现边录边转写 (实时语音识别)。
 * <p>
 * 使用 {@link RecognizerIntent#EXTRA_PARTIAL_RESULTS} 机制,
 * 每识别出一个中间片段即通过 EventBus 发送 {@link AudioTranscribeEvent},
 * 外部 UI 订阅该事件即可实现文本实时滚动追加。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   SpeechRecognizerManager srm = new SpeechRecognizerManager(context);
 *   srm.startListening();  // 开始边录边转写
 *   // ... 订阅 AudioTranscribeEvent ...
 *   srm.stopListening();   // 停止, 获取最终结果
 *   srm.destroy();         // 释放资源
 * }</pre>
 *
 * <h3>与 AudioRecorderService 的关系</h3>
 * 本类独立于 MediaRecorder, 直接复用系统语音引擎。两者可配合使用:
 * AudioRecorderService 负责保存高清音频文件, SpeechRecognizerManager 负责实时转写。
 * <p>
 * 注意: 部分设备上 MediaRecorder 与 SpeechRecognizer 存在麦克风互斥,
 * 此时优先使用 SpeechRecognizer。
 */
public class SpeechRecognizerManager implements RecognitionListener {

    private static final String TAG = "SpeechRecManager";

    private final Context context;
    private SpeechRecognizer recognizer;

    private StringBuilder fullTranscript;
    private boolean isListening;
    private boolean destroyed;
    private int errorCount; // 连续失败次数

    public SpeechRecognizerManager(Context context) {
        this.context = context.getApplicationContext();
        this.fullTranscript = new StringBuilder();
    }

    /** 延迟创建 SpeechRecognizer, 避免构造函数中 crash。 */
    private SpeechRecognizer getRecognizer() {
        if (recognizer == null && !destroyed) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                recognizer.setRecognitionListener(this);
            } catch (Exception e) {
                Log.w(TAG, "SpeechRecognizer not available: " + e.getMessage());
                recognizer = null;
            }
        }
        return recognizer;
    }

    // ================================================================
    // 公开方法
    // ================================================================

    /** 开始边录边转写。需在主线程调用。 */
    public void startListening() {
        if (destroyed) return;
        if (isListening) return;

        // 检查设备是否支持语音识别
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            EventBus.getDefault().post(new AudioTranscribeEvent(
                "[设备未安装语音识别服务, 请安装\"Google\"应用或讯飞输入法后重试]", true));
            return;
        }

        if (errorCount >= 3) {
            EventBus.getDefault().post(new AudioTranscribeEvent(
                "[语音服务连续异常, 请稍后重试]", true));
            return;
        }

        SpeechRecognizer sr = getRecognizer();
        if (sr == null) {
            errorCount = 3;
            EventBus.getDefault().post(new AudioTranscribeEvent(
                "[语音服务创建失败, 请在下方输入框打字]", true));
            return;
        }

        fullTranscript.setLength(0);
        isListening = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
            context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000);

        try {
            recognizer.startListening(intent);
        } catch (SecurityException e) {
            isListening = false;
            EventBus.getDefault().post(
                new AudioTranscribeEvent("[缺少录音权限, 请在系统设置中授权]", true));
        } catch (Exception e) {
            isListening = false;
            EventBus.getDefault().post(
                new AudioTranscribeEvent("[语音服务不可用: " + e.getMessage() + "]", true));
        }
    }

    /** 停止监听, SpeechRecognizer 会回调 onResults 返回最终文本。 */
    public void stopListening() {
        if (destroyed || !isListening) return;
        SpeechRecognizer sr = recognizer;
        if (sr == null) {
            isListening = false;
            return;
        }
        try {
            sr.stopListening();
        } catch (Exception e) {
            Log.w(TAG, "stopListening: " + e.getMessage());
            isListening = false;
        }
        // isListening 在 onResults / onError 中置回 false
    }

    /** 取消当前识别(不触发 onResults)。 */
    public void cancel() {
        if (destroyed) return;
        recognizer.cancel();
        isListening = false;
    }

    /** 释放底层 SpeechRecognizer 资源。调用后不可再用。 */
    public void destroy() {
        destroyed = true;
        isListening = false;
        SpeechRecognizer sr = recognizer;
        recognizer = null;
        if (sr == null) return;
        try {
            sr.cancel();
        } catch (Exception ignored) {}
        try {
            sr.destroy();
        } catch (Exception ignored) {}
    }

    /** 返回本次会话累积的完整转写文本。 */
    public String getFullTranscript() {
        return fullTranscript.toString();
    }

    public boolean isListening() {
        return isListening;
    }

    // ================================================================
    // RecognitionListener 回调
    // ================================================================

    @Override
    public void onReadyForSpeech(Bundle params) {
        isListening = true;
    }

    @Override
    public void onBeginningOfSpeech() {
        // 用户开始说话 —— 可用于 UI 波形动画触发
    }

    /**
     * 音量变化回调 (0~10)。
     * 可结合 EventBus 发送音量值供 UI 绘制波形。
     */
    @Override
    public void onRmsChanged(float rmsdB) {
        // 预留: 后续可发送 AudioLevelEvent 给 UI 绘制波形
    }

    /**
     * 核心回调: 实时中间识别结果。
     * 每次语音片段变化时触发, 实现"边录边转写"效果。
     */
    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String partial = matches.get(0);
            // 发送中间结果 (isFinal=false) — UI 可以实时滚动展示
            EventBus.getDefault().post(new AudioTranscribeEvent(partial, false));
        }
    }

    /** 最终识别结果 (用户停止说话后回调)。 */
    @Override
    public void onResults(Bundle results) {
        isListening = false;
        ArrayList<String> matches = results.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String finalText = matches.get(0);
            fullTranscript.append(finalText);
            // 发送最终结果 (isFinal=true) — UI 可确认追加
            EventBus.getDefault().post(new AudioTranscribeEvent(finalText, true));
        } else {
            EventBus.getDefault().post(
                new AudioTranscribeEvent("", true));
        }
    }

    @Override
    public void onError(int errorCode) {
        isListening = false;
        errorCount++;
        String msg;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                msg = "[音频错误]";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                msg = "[客户端错误]";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                msg = "[缺少录音权限]";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                msg = "[网络错误]";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                msg = "[网络超时]";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                msg = "[未识别到语音]";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                msg = "[引擎繁忙, 请稍后重试]";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                msg = "[服务端错误]";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                msg = "[说话超时]";
                break;
            default:
                msg = "[未知错误: " + errorCode + "]";
                break;
        }
        // 错误也通过 EventBus 通知 UI, isFinal=true 表示本次识别结束
        EventBus.getDefault().post(new AudioTranscribeEvent(msg, true));
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        // 原始音频数据 — 预留用于本地音频分析
    }

    @Override
    public void onEndOfSpeech() {
        // 用户停止说话, SpeechRecognizer 即将返回最终结果
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        // 厂商扩展事件 — 一般无需处理
    }
}
