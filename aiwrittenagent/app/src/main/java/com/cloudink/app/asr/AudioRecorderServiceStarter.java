package com.cloudink.app.asr;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/** 启动 / 停止 {@link AudioRecorderService} 前台录音。 */
public final class AudioRecorderServiceStarter {

    private AudioRecorderServiceStarter() {}

    public static void start(Context context) {
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(AudioRecorderService.ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(AudioRecorderService.ACTION_STOP);
        context.startService(intent);
    }
}
