package com.cloudink.app.asr;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 百度按住说话：录 PCM → 松手后调用短语音识别 API。
 */
public class BaiduRealtimeAsrHelper {

    private static final String TAG = "BaiduRealtimeAsr";

    private static final String ASR_URL = "https://vop.baidu.com/server_api";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    /** 最短有效录音约 0.4 秒 */
    private static final int MIN_PCM_BYTES = SAMPLE_RATE * 2 / 5;

    private final Context permissionContext;
    private final OkHttpClient client;
    private final Object recordLock = new Object();
    private final ExecutorService recognizeExecutor = Executors.newSingleThreadExecutor();

    private AudioRecord audioRecord;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private ByteArrayOutputStream audioBuffer;
    private Thread recordingThread;
    private RealtimeCallback activeCallback;
    private final AtomicBoolean isRecognizing = new AtomicBoolean(false);

    public interface RealtimeCallback {
        void onStart();
        void onVolumeChanged(int volume);
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String error);
    }

    public BaiduRealtimeAsrHelper(Context context) {
        this.permissionContext = context;
        client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    public void startRecording(RealtimeCallback callback) {
        if (callback == null) return;

        synchronized (recordLock) {
            if (isRecording.get()) {
                Log.w(TAG, "已在录音中，忽略重复开始");
                return;
            }
            if (isRecognizing.get()) {
                callback.onError("请等待上一段语音识别完成后再按");
                return;
            }

            if (!BaiduAsrConfig.isConfigured()) {
                callback.onError("未配置百度语音：请在 local.properties 填写 baidu.asr.api.key");
                return;
            }

            if (!hasRecordPermission()) {
                callback.onError("NEED_MIC_PERMISSION");
                return;
            }

            activeCallback = callback;
            audioBuffer = new ByteArrayOutputStream();

            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize <= 0) {
                bufferSize = SAMPLE_RATE * 2;
            }
            final int readBufferSize = bufferSize;

            AudioRecord record = createAudioRecord(readBufferSize);
            if (record == null || record.getState() != AudioRecord.STATE_INITIALIZED) {
                safeRelease(record);
                activeCallback = null;
                callback.onError("无法打开麦克风，请确认已允许录音权限并重试");
                return;
            }

            audioRecord = record;
            try {
                record.startRecording();
            } catch (SecurityException se) {
                safeRelease(record);
                audioRecord = null;
                activeCallback = null;
                callback.onError("NEED_MIC_PERMISSION");
                return;
            } catch (IllegalStateException e) {
                safeRelease(record);
                audioRecord = null;
                activeCallback = null;
                callback.onError("麦克风被占用，请关闭其他录音应用后重试");
                return;
            }

            isRecording.set(true);
            callback.onStart();
            Log.d(TAG, "开始录音");

            recordingThread = new Thread(() -> runRecordLoop(readBufferSize), "baidu-pcm-record");
            recordingThread.start();
        }
    }

    /** 仅发送停止信号，不在调用线程上 join（避免卡住 UI）。 */
    public void stopRecording() {
        if (!isRecording.getAndSet(false)) {
            return;
        }
        Log.d(TAG, "停止录音信号");
    }

    public boolean isBusy() {
        return isRecording.get() || isRecognizing.get();
    }

    private void runRecordLoop(int bufferSize) {
        RealtimeCallback callback = activeCallback;
        byte[] buffer = new byte[bufferSize];
        AudioRecord record;

        synchronized (recordLock) {
            record = audioRecord;
        }

        try {
            while (isRecording.get() && record != null) {
                int bytesRead;
                try {
                    bytesRead = record.read(buffer, 0, buffer.length);
                } catch (Exception e) {
                    Log.e(TAG, "read failed", e);
                    break;
                }
                if (bytesRead > 0 && audioBuffer != null) {
                    audioBuffer.write(buffer, 0, bytesRead);
                    if (callback != null) {
                        callback.onVolumeChanged(calculateVolume(buffer, bytesRead));
                    }
                }
            }
        } finally {
            synchronized (recordLock) {
                safeRelease(record);
                if (audioRecord == record) {
                    audioRecord = null;
                }
            }

            byte[] pcm = audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
            Log.d(TAG, "录音结束 PCM=" + pcm.length);

            if (callback != null) {
                if (pcm.length < MIN_PCM_BYTES) {
                    callback.onError("录音太短，请按住说话至少 1 秒再松开");
                } else {
                    recognizeAudio(pcm, callback);
                }
            }
            activeCallback = null;
        }
    }

    private AudioRecord createAudioRecord(int bufferSize) {
        int size = Math.max(bufferSize * 2, SAMPLE_RATE);
        int[] sources = {
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.DEFAULT
        };
        for (int source : sources) {
            try {
                AudioRecord ar = new AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, size);
                if (ar.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "AudioRecord ok, source=" + source);
                    return ar;
                }
                safeRelease(ar);
            } catch (Exception e) {
                Log.w(TAG, "AudioRecord source " + source + " failed: " + e.getMessage());
            }
        }
        return null;
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(permissionContext,
            android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void recognizeAudio(byte[] audioData, RealtimeCallback callback) {
        if (!isRecognizing.compareAndSet(false, true)) {
            callback.onError("请等待上一段语音识别完成后再按");
            return;
        }
        recognizeExecutor.execute(() -> {
            try {
                String accessToken = BaiduTokenProvider.getAccessToken();
                String result = callAsrApi(audioData, accessToken);
                JSONObject json = new JSONObject(result);
                int errNo = json.optInt("err_no", -1);

                if (errNo == 0 && json.has("result")) {
                    JSONArray results = json.getJSONArray("result");
                    if (results.length() > 0) {
                        callback.onFinalResult(results.getString(0));
                    } else {
                        callback.onError("未识别到语音，请靠近麦克风清晰说话");
                    }
                } else {
                    String errMsg = json.optString("err_msg", "未知错误");
                    callback.onError(BaiduApiErrors.formatAsrError(errNo, errMsg));
                }
            } catch (Exception e) {
                Log.e(TAG, "识别异常", e);
                callback.onError("识别失败: " + e.getMessage());
            } finally {
                isRecognizing.set(false);
            }
        });
    }

    static String formatBaiduAsrError(int errNo, String errMsg) {
        return BaiduApiErrors.formatAsrError(errNo, errMsg);
    }

    private String callAsrApi(byte[] audioData, String accessToken) throws Exception {
        JSONObject params = new JSONObject();
        params.put("format", "pcm");
        params.put("rate", SAMPLE_RATE);
        params.put("channel", 1);
        params.put("cuid", "android_cloudink_app");
        params.put("token", accessToken);
        params.put("speech", Base64.encodeToString(audioData, Base64.NO_WRAP));
        params.put("len", audioData.length);
        params.put("dev_pid", 1537);

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
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("API 请求失败 HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private static int calculateVolume(byte[] buffer, int length) {
        if (length < 2) return 0;
        long peak = 0;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            int abs = Math.abs(sample);
            if (abs > peak) peak = abs;
            samples++;
        }
        if (samples == 0) return 0;
        double norm = peak / 5000.0;
        int volume = (int) (Math.pow(Math.min(1.0, norm), 0.35) * 100);
        return Math.min(100, Math.max(volume, peak > 150 ? 12 : 0));
    }

    private static void safeRelease(AudioRecord record) {
        if (record == null) return;
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } catch (Exception ignored) {
        }
        try {
            record.release();
        } catch (Exception ignored) {
        }
    }

    public void release() {
        stopRecording();
        recognizeExecutor.shutdown();
    }

    public boolean isRecording() {
        return isRecording.get();
    }
}
