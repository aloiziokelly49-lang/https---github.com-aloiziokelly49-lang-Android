package com.cloudink.app.ui.editor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.cloudink.app.asr.AudioRecorderServiceStarter;
import com.cloudink.app.asr.BaiduAsrConfig;
import com.cloudink.app.asr.BaiduAsrHelper;
import com.cloudink.app.asr.SpeechInputHelper;
import com.cloudink.app.asr.SpeechRecognizerManager;
import com.cloudink.app.data.repository.CloudInkRepository;
import com.cloudink.app.event.AudioTranscribeEvent;
import com.cloudink.app.event.RecordingStateEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * 手写页语音采集：前台 Service + MediaRecorder 后台保活，
 * SpeechRecognizer 边录边转写，麦克风互斥时降级为停止后百度转写文件。
 * 如果百度转写也不可用，则提示用户使用输入法语音。
 */
public class EditorVoiceCaptureController {

    private final Context appContext;
    private final EditorVoiceState voice;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechRecognizerManager speechManager;
    private final BaiduAsrHelper baiduHelper = new BaiduAsrHelper();
    private final StringBuilder sessionTranscript = new StringBuilder();

    private boolean capturing;
    private boolean stopRequested;
    private boolean serviceStopped;
    private boolean speechStopped;
    private String recordedFilePath;
    private boolean speechStreamingDisabled;
    private boolean baiduRunning;

    public EditorVoiceCaptureController(Context context, EditorVoiceState voice) {
        this.appContext = context.getApplicationContext();
        this.voice = voice;
    }

    public void register() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    public void unregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    public boolean isCapturing() {
        return capturing;
    }

    // 开始采集：启动前台 Service 录音，边录边转写（如果可用），
    // 更新 UI 状态
    public void startCapture() {
        if (capturing) {
            voice.setPanelExpanded(true);
            return;
        }

        capturing = true;
        stopRequested = false;
        serviceStopped = false;
        speechStopped = false;
        recordedFilePath = null;
        speechStreamingDisabled = false;
        baiduRunning = false;
        sessionTranscript.setLength(0);

        voice.setRecording(true);
        voice.setPanelExpanded(true);
        voice.setTranscribing(false);
        voice.setLiveTranscript("");
        voice.refreshAppendEnabled();

        AudioRecorderServiceStarter.start(appContext);

        // 优先使用系统 SpeechRecognizer 边录边转写，
        // 麦克风被占用时降级为停止后百度转写
        if (SpeechInputHelper.hasStreamingRecognizer(appContext)) {
            if (speechManager == null) {
                speechManager = new SpeechRecognizerManager(appContext);
            }
            speechStopped = false;
            // 开始边录边转写，UI 显示状态
            speechManager.startListening();
            voice.setStatusMessage("边录边转写中（可息屏，后台继续录音）");
        } else if (BaiduAsrConfig.isConfigured()) {
            speechStreamingDisabled = true;
            speechStopped = true;
            // 无法边录边转，提示用户停止后使用百度转写
            voice.setStatusMessage("后台录音中，点「停止」后百度转写");
        } else {
            speechStreamingDisabled = true;
            speechStopped = true;
            // 无法边录边转，提示用户停止后使用输入法语音
            voice.setStatusMessage(SpeechInputHelper.imeVoiceHint(appContext));
        }
    }

    // 停止采集：停止 Service 录音，停止边录边转（如果在用），
    public void stopCapture() {
        if (!capturing) return;
        stopRequested = true;
        voice.setTranscribing(true);
        voice.setStatusMessage("正在结束录音并转写…");
        voice.refreshAppendEnabled();

        if (speechManager != null && speechManager.isListening()) {
            speechStopped = false;
            speechManager.stopListening();
        } else {
            speechStopped = true;
        }

        AudioRecorderServiceStarter.stop(appContext);
        tryFinalize();
    }

    public void interruptCapture() {
        if (!capturing) return;
        stopRequested = true;
        capturing = false;
        voice.setRecording(false);
        voice.setTranscribing(false);

        if (speechManager != null) {
            try {
                speechManager.stopListening();
            } catch (Exception ignored) {}
        }
        AudioRecorderServiceStarter.stop(appContext);
        mergeTranscriptToVoice();
        voice.setStatusMessage("已打断，可编辑后点「插入草稿」");
        voice.refreshAppendEnabled();
    }

    public void cancelCapture() {
        stopRequested = true;
        capturing = false;
        sessionTranscript.setLength(0);
        voice.setLiveTranscript("");
        voice.setRecording(false);
        voice.setTranscribing(false);
        voice.setStatusMessage("");
        voice.refreshAppendEnabled();

        if (speechManager != null) {
            try {
                speechManager.cancel();
            } catch (Exception ignored) {}
        }
        AudioRecorderServiceStarter.stop(appContext);
    }

    public void clearSession() {
        sessionTranscript.setLength(0);
    }

    // 一键拼接到当前转写结果末尾，
    // 适用于输入法语音等外部转写结果
    public void appendExternalSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()) return;
        String cleaned = segment.trim();
        if (cleaned.startsWith("[")) return;

        if (sessionTranscript.length() > 0 && !sessionTranscript.toString().endsWith("\n")) {
            sessionTranscript.append('\n');
        }
        sessionTranscript.append(cleaned);
        voice.setLiveTranscript(sessionTranscript.toString());
        voice.refreshAppendEnabled();
    }

    public void release() {
        if (capturing) {
            cancelCapture();
        }
        if (speechManager != null) {
            speechManager.destroy();
            speechManager = null;
        }
        baiduHelper.release();
        unregister();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRecordingState(RecordingStateEvent event) {
        if (event.state == RecordingStateEvent.State.ERROR) {
            if (capturing) {
                voice.setStatusMessage("录音错误: " + event.errorMsg);
            }
            serviceStopped = true;
            recordedFilePath = null;
            tryFinalize();
            return;
        }
        if (event.state == RecordingStateEvent.State.STOPPED) {
            serviceStopped = true;
            recordedFilePath = event.filePath;
            tryFinalize();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAudioTranscribe(AudioTranscribeEvent event) {
        if (!capturing && !stopRequested) return;

        String seg = event.transcribedSegment;
        if (seg == null) return;

        if (seg.startsWith("[")) {
            if (seg.contains("音频错误") || seg.contains("客户端错误")) {
                speechStreamingDisabled = true;
                if (BaiduAsrConfig.isConfigured()) {
                    voice.setStatusMessage("实时转写不可用，后台录音继续；停止后百度转写");
                } else {
                    voice.setStatusMessage("实时转写不可用，请用「输入法语音」");
                }
            }
            if (event.isFinal) {
                speechStopped = true;
                tryFinalize();
            }
            return;
        }

        if (event.isFinal) {
            if (!seg.isEmpty()) {
                if (sessionTranscript.length() > 0) {
                    sessionTranscript.append('\n');
                }
                sessionTranscript.append(seg);
            }
            voice.setLiveTranscript(sessionTranscript.toString());
            voice.refreshAppendEnabled();

            if (capturing && !stopRequested && !speechStreamingDisabled
                && speechManager != null) {
                speechManager.startListening();
            } else {
                speechStopped = true;
                tryFinalize();
            }
        } else {
            String display = sessionTranscript.toString();
            if (!display.isEmpty() && !seg.isEmpty()) {
                display = display + seg;
            } else if (!seg.isEmpty()) {
                display = seg;
            }
            voice.setLiveTranscript(display);
            voice.refreshAppendEnabled();
        }
    }

    private void tryFinalize() {
        if (!stopRequested) return;
        if (!serviceStopped) return;
        if (!speechStopped && speechManager != null && speechManager.isListening()) return;
        if (baiduRunning) return;

        mergeTranscriptToVoice();
        String text = voice.getLiveTranscript() != null ? voice.getLiveTranscript().trim() : "";

        if (text.isEmpty() && recordedFilePath != null && BaiduAsrConfig.isConfigured()) {
            baiduRunning = true;
            voice.setTranscribing(true);
            voice.setStatusMessage("百度语音转写中…");
            final String path = recordedFilePath;
            baiduHelper.recognizeAudioFile(path, new BaiduAsrHelper.AsrCallback() {
                @Override
                public void onSuccess(String result) {
                    mainHandler.post(() -> {
                        baiduRunning = false;
                        if (result != null && !result.trim().isEmpty()) {
                            sessionTranscript.setLength(0);
                            sessionTranscript.append(result.trim());
                            voice.setLiveTranscript(sessionTranscript.toString());
                        }
                        completeStop();
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        baiduRunning = false;
                        voice.setStatusMessage(error != null ? error : "百度转写失败");
                        completeStop();
                    });
                }
            });
            return;
        }

        completeStop();
    }

    private void mergeTranscriptToVoice() {
        String session = sessionTranscript.toString().trim();
        String live = voice.getLiveTranscript() != null ? voice.getLiveTranscript().trim() : "";
        if (session.isEmpty() && live.isEmpty()) {
            voice.setLiveTranscript("");
        } else if (session.isEmpty()) {
            voice.setLiveTranscript(live);
        } else if (live.isEmpty() || live.equals(session)) {
            voice.setLiveTranscript(session);
        } else if (live.contains(session)) {
            voice.setLiveTranscript(live);
        } else {
            voice.setLiveTranscript(session + "\n" + live);
        }
        voice.refreshAppendEnabled();
    }

    private void completeStop() {
        if (recordedFilePath != null) {
            String transcript = voice.getLiveTranscript();
            CloudInkRepository.get(appContext).saveAudioRecord(
                recordedFilePath, 0L, transcript, null);
        }
        capturing = false;
        stopRequested = false;
        voice.setRecording(false);
        voice.setTranscribing(false);
        String t = voice.getLiveTranscript();
        voice.setStatusMessage(t != null && !t.trim().isEmpty()
            ? "转写完成，可编辑后插入草稿"
            : "未识别到内容，可点「输入法语音」重试");
        voice.refreshAppendEnabled();
    }
}
