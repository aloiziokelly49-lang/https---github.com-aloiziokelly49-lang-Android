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
 * 封装 Android 系统内置 SpeechRecognizer, 
 * 实现边录边转写 (实时语音识别)。
 */
public class SpeechRecognizerManager implements RecognitionListener {

    private static final String TAG = "SpeechRecManager";

    private final Context context;

    // 系统 SpeechRecognizer 实例
    private SpeechRecognizer recognizer;

    private StringBuilder fullTranscript;
    private boolean isListening;
    private boolean destroyed;
    private int errorCount;

    public SpeechRecognizerManager(Context context) {
        this.context = context.getApplicationContext();
        this.fullTranscript = new StringBuilder();
    }

    private SpeechRecognizer getRecognizer() {
        if (recognizer == null && !destroyed) {
            try {
                // 创建系统 SpeechRecognizer 实例, 
                // 并配置监听器回调
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                recognizer.setRecognitionListener(this);
            } catch (Exception e) {
                Log.w(TAG, "SpeechRecognizer not available: " + e.getMessage());
                recognizer = null;
            }
        }
        return recognizer;
    }

    /** 开始边录边转写。需在主线程调用。 */
    public void startListening() {
        if (destroyed) return;
        if (isListening) return;

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

        //SpeechRecognizer 需要通过 Intent，
        //来配置识别参数，比如语言、模型等。

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        //开启 边录边转写 配置
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
            context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000);

        try {

            // 启动监听，系统会调用
            // RecognitionListener 的回调方法，
            // 来返回识别结果，实现边录边转写。
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
    }

    /** 取消识别 */
    public void cancel() {
        if (destroyed) return;
        recognizer.cancel();
        isListening = false;
    }

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
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    /**
     * 核心回调: 
     * 每次语音片段变化时触发, 
     * 调用 onPartialResults回调，
     * 会返回当前的部分识别结果，
     * 实现"边录边转写"效果。
     */
    @Override
    public void onPartialResults(Bundle partialResults) {
        // 部分识别结果
        ArrayList<String> matches = partialResults.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String partial = matches.get(0);
            // 发送中间结果— UI 可以实时滚动展示
            EventBus.getDefault().post(new AudioTranscribeEvent(partial, false));
        }
    }

    /** 返回最终识别结果 (用户停止说话后回调)。 */
    @Override
    public void onResults(Bundle results) {
        isListening = false;
        // 最终识别结果
        ArrayList<String> matches = results.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String finalText = matches.get(0);
            fullTranscript.append(finalText);
            // 发送最终识别结果
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
        EventBus.getDefault().post(new AudioTranscribeEvent(msg, true));
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        // 用户停止说话, SpeechRecognizer 即将返回最终结果
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
}
