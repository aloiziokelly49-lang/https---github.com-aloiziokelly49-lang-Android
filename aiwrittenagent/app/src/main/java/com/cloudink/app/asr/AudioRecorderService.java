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
 * <h3>生命周期</h3>
 * <ol>
 *   <li>外部通过 {@link #ACTION_START} 启动 → 创建 MediaRecorder → 开始录制</li>
 *   <li>外部通过 {@link #ACTION_STOP} 停止 → 停止录制 → stopSelf()</li>
 *   <li>录制过程中通过 EventBus 发送 {@link RecordingStateEvent}</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 *   Intent intent = new Intent(context, AudioRecorderService.class);
 *   intent.setAction(AudioRecorderService.ACTION_START);
 *   ContextCompat.startForegroundService(context, intent);
 *   // ... 稍后
 *   intent.setAction(AudioRecorderService.ACTION_STOP);
 *   context.startService(intent);
 * }</pre>
 */
public class AudioRecorderService extends Service {

    // ---- 广播 Action ----
    public static final String ACTION_START = "com.cloudink.app.action.START_RECORDING";
    public static final String ACTION_STOP  = "com.cloudink.app.action.STOP_RECORDING";

    // ---- 通知 ----
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
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(AUDIO_SOURCE);
            mediaRecorder.setOutputFormat(OUTPUT_FORMAT);
            mediaRecorder.setAudioEncoder(AUDIO_ENCODER);
            mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
            mediaRecorder.setAudioEncodingBitRate(BIT_RATE);
            mediaRecorder.setOutputFile(currentFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            startForeground(NOTIFY_ID, buildNotification("录制中..."));
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

        // 安全停止 — MediaRecorder 有时会在 stop() 时抛 RuntimeException
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (RuntimeException ignored) {
            // 可能已被释放或录制时间过短
        } finally {
            releaseMediaRecorder();
            isRecording = false;
        }

        // 停止前台, 保留通知直到用户操作
        stopForeground(STOP_FOREGROUND_DETACH);

        // 校验文件有效性
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
                // 容错: MediaRecorder 状态机冲突时忽略
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

    private Notification buildNotification(String contentText) {
        Intent intent = new Intent(this, HomeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

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
