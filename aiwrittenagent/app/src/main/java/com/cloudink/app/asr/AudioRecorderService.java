package com.cloudink.app.asr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.cloudink.app.R;
import com.cloudink.app.event.RecordingStateEvent;
import com.cloudink.app.ui.home.HomeActivity;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前台 Service —— 使用 MediaRecorder 实现息屏/切后台不中断的音频采集。
 *
 */
public class AudioRecorderService extends Service {

    public static final String ACTION_START = "com.cloudink.app.action.START_RECORDING";
    public static final String ACTION_STOP  = "com.cloudink.app.action.STOP_RECORDING";

    private static final String CHANNEL_ID   = "cloudink_recording";
    private static final String CHANNEL_NAME = "录音状态";
    private static final int    NOTIFY_ID    = 2001;

    // ---- 音频参数 ----
    private static final int AUDIO_SOURCE   = MediaRecorder.AudioSource.MIC;
    private static final int OUTPUT_FORMAT  = MediaRecorder.OutputFormat.MPEG_4;
    private static final int AUDIO_ENCODER  = MediaRecorder.AudioEncoder.AAC;
    private static final int SAMPLE_RATE    = 44100;
    private static final int BIT_RATE       = 128000;

    private MediaRecorder mediaRecorder;
    private String currentFilePath;
    private boolean isRecording;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_START:
                startRecording();
                break;
            case ACTION_STOP:
                stopRecording();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // 不绑定
    }

    // ================================================================
    // 开始录音
    // ================================================================
    private void startRecording() {
        if (isRecording) return;

        currentFilePath = generateFilePath();

        try {
            // 初始化 MediaRecorder，
            // 配置参数，比如音频源、输出格式、编码器、采样率、比特率等，
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(AUDIO_SOURCE);
            mediaRecorder.setOutputFormat(OUTPUT_FORMAT);
            mediaRecorder.setAudioEncoder(AUDIO_ENCODER);
            mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
            mediaRecorder.setAudioEncodingBitRate(BIT_RATE);
            mediaRecorder.setOutputFile(currentFilePath);
            mediaRecorder.prepare();
            // 启动录音
            mediaRecorder.start();

            isRecording = true;

            // 启动前台服务，显示持续通知，实现息屏/切后台不中断录音
            startForeground(NOTIFY_ID, buildNotification("录制中..."));

            // 发送录音开始事件
            EventBus.getDefault().post(RecordingStateEvent.started());

        } catch (IOException e) {
            String msg = "MediaRecorder 启动失败: " + e.getMessage();
            EventBus.getDefault().post(RecordingStateEvent.error(msg));
            releaseMediaRecorder();
        }
    }

    // ================================================================
    // 停止录音
    // ================================================================
    private void stopRecording() {
        if (!isRecording) return;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (RuntimeException ignored) {
        } finally {
            releaseMediaRecorder();
            isRecording = false;
        }

        // 停止前台, 保留通知直到用户操作
        stopForeground(STOP_FOREGROUND_DETACH);

        File file = new File(currentFilePath);
        if (file.exists() && file.length() > 0) {
            EventBus.getDefault().post(RecordingStateEvent.stopped(currentFilePath));
        } else {
            EventBus.getDefault().post(RecordingStateEvent.error("录音文件为空或不存在"));
        }

        stopSelf();
    }

    // ================================================================
    // 工具方法
    // ================================================================
    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception ignored) {
            }
            mediaRecorder = null;
        }
    }

    /** 按时间戳生成录音文件路径: /data/data/.../cache/audio/yyyyMMdd_HHmmss.m4a */
    private String generateFilePath() {
        File dir = new File(getCacheDir(), "audio");
        if (!dir.exists()) dir.mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "REC_" + ts + ".m4a").getAbsolutePath();
    }

    // ================================================================
    // 通知
    // ================================================================
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("云墨录音服务通知");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    // 构建前台服务通知
    private Notification buildNotification(String contentText) {
        // 点击通知，就会跳转到 HomeActivity
        Intent intent = new Intent(this, HomeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // 构建通知，显示录音状态，设置点击事件等
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("云墨录音")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @Override
    public void onDestroy() {
        releaseMediaRecorder();
        isRecording = false;
        super.onDestroy();
    }
}
