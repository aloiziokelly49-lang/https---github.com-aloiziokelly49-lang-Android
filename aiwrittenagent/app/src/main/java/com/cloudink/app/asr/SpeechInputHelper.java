package com.cloudink.app.asr;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

/**
 * 检测本机语音识别能力，并构造系统语音输入 Intent。
 */
public final class SpeechInputHelper {

    private SpeechInputHelper() {}

    /** 支持按住说话、边录边转的 SpeechRecognizer。 */
    public static boolean hasStreamingRecognizer(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    /** 支持弹出系统语音识别界面的 Activity。 */
    public static boolean hasSystemSpeechActivity(Context context) {
        return createRecognizeIntent().resolveActivity(context.getPackageManager()) != null;
    }

    public static boolean isAnySpeechAvailable(Context context) {
        return hasStreamingRecognizer(context) || hasSystemSpeechActivity(context);
    }

    public static Intent createRecognizeIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        return intent;
    }

    public static boolean isProbablyEmulator() {
        String fp = Build.FINGERPRINT;
        String model = Build.MODEL;
        String hw = Build.HARDWARE;
        String product = Build.PRODUCT;
        return fp != null && (fp.contains("generic") || fp.contains("unknown"))
            || model != null && (model.contains("google_sdk") || model.contains("Emulator")
                || model.contains("Android SDK built for x86"))
            || hw != null && (hw.contains("goldfish") || hw.contains("ranchu"))
            || product != null && product.contains("sdk");
    }

    public static boolean isProbablyVivoDevice() {
        String m = Build.MANUFACTURER;
        String b = Build.BRAND;
        return (m != null && (m.equalsIgnoreCase("vivo") || m.equalsIgnoreCase("bbk")))
            || (b != null && (b.equalsIgnoreCase("vivo") || b.equalsIgnoreCase("iqoo")));
    }

    public static String unavailableMessage(Context context) {
        if (isProbablyEmulator()) {
            return context.getString(com.cloudink.app.R.string.voice_hint_emulator);
        }
        if (isProbablyVivoDevice() && !isAnySpeechAvailable(context)) {
            return context.getString(com.cloudink.app.R.string.voice_hint_vivo_ime);
        }
        if (!hasStreamingRecognizer(context) && hasSystemSpeechActivity(context)) {
            return context.getString(com.cloudink.app.R.string.voice_hint_system_only);
        }
        return context.getString(com.cloudink.app.R.string.voice_hint_no_service);
    }

    /** 推荐用户使用输入法麦克风（搜狗/讯飞等）录入到转写区。 */
    public static String imeVoiceHint(Context context) {
        if (isProbablyVivoDevice()) {
            return context.getString(com.cloudink.app.R.string.voice_hint_vivo_ime_short);
        }
        return context.getString(com.cloudink.app.R.string.voice_hint_keyboard_ime);
    }
}
