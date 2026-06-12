package com.cloudink.app.ui.asr;



import android.content.Context;

import android.os.Handler;

import android.os.Looper;

import androidx.lifecycle.LiveData;

import androidx.lifecycle.MutableLiveData;

import androidx.lifecycle.ViewModel;



import com.cloudink.app.CloudInkApplication;

import com.cloudink.app.asr.BaiduAsrConfig;

import com.cloudink.app.asr.BaiduRealtimeAsrHelper;

import com.cloudink.app.asr.SpeechInputHelper;

import com.cloudink.app.asr.SpeechRecognizerManager;

import com.cloudink.app.event.AudioTranscribeEvent;

import com.cloudink.app.event.RecordingStateEvent;



import org.greenrobot.eventbus.EventBus;

import org.greenrobot.eventbus.Subscribe;

import org.greenrobot.eventbus.ThreadMode;



/**

 * 语音转写库 ViewModel：优先百度语音（已配置时），否则系统 SpeechRecognizer。

 */

public class VoiceTranscribeViewModel extends ViewModel {



    private SpeechRecognizerManager speechManager;

    private BaiduRealtimeAsrHelper baiduAsr;

    private boolean useBaiduAsr;



    private final MutableLiveData<String> resultText = new MutableLiveData<>("");

    private final MutableLiveData<String> liveTranscript = new MutableLiveData<>("");

    private final MutableLiveData<Boolean> isRecording = new MutableLiveData<>(false);

    private final MutableLiveData<String> voiceFeedback = new MutableLiveData<>();

    private final MutableLiveData<Boolean> baiduReady = new MutableLiveData<>(false);



    private final StringBuilder fullTranscript = new StringBuilder();

    private boolean pendingAppendAfterStop;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable finishAfterStop = this::finishVoiceInputAfterStop;



    public VoiceTranscribeViewModel() {

        EventBus.getDefault().register(this);

        baiduReady.setValue(BaiduAsrConfig.isConfigured());

        useBaiduAsr = BaiduAsrConfig.isConfigured();

    }



    public LiveData<String> getResultText() { return resultText; }

    public LiveData<String> getLiveTranscript() { return liveTranscript; }

    public LiveData<Boolean> getIsRecording() { return isRecording; }

    public LiveData<String> getVoiceFeedback() { return voiceFeedback; }

    public LiveData<Boolean> getBaiduReady() { return baiduReady; }



    public boolean prefersBaiduAsr() {

        return useBaiduAsr;

    }



    public void setResultText(String text) {

        resultText.setValue(text != null ? text : "");

    }



    public void appendSegment(String segment) {

        if (segment == null || segment.trim().isEmpty()) return;

        String cleaned = cleanTranscriptForInput(segment);

        if (cleaned.isEmpty()) return;



        String current = resultText.getValue();

        if (current == null) current = "";

        if (!current.isEmpty() && !current.endsWith("\n")) {

            current += "\n";

        }

        resultText.setValue(current + cleaned);

        voiceFeedback.setValue(null);

    }



    public void startRecording() {

        if (Boolean.TRUE.equals(isRecording.getValue())) return;



        Context ctx = CloudInkApplication.getInstance();

        fullTranscript.setLength(0);

        pendingAppendAfterStop = false;

        handler.removeCallbacks(finishAfterStop);

        liveTranscript.setValue("");



        if (useBaiduAsr && BaiduAsrConfig.isConfigured()) {

            startBaiduRecording(ctx);

            return;

        }



        if (!SpeechInputHelper.hasStreamingRecognizer(ctx)) {

            voiceFeedback.setValue(SpeechInputHelper.unavailableMessage(ctx));

            return;

        }



        if (speechManager == null) {

            speechManager = new SpeechRecognizerManager(ctx);

        }

        isRecording.setValue(true);

        liveTranscript.setValue("请说话…");

        try {

            speechManager.startListening();

        } catch (Exception e) {

            isRecording.setValue(false);

            voiceFeedback.setValue("无法启动语音识别：" + e.getMessage());

        }

    }



    private void startBaiduRecording(Context ctx) {

        if (baiduAsr == null) {

            baiduAsr = new BaiduRealtimeAsrHelper(ctx);

        }

        isRecording.setValue(true);

        liveTranscript.setValue("请说话…（百度语音）");

        baiduAsr.startRecording(new BaiduRealtimeAsrHelper.RealtimeCallback() {

            @Override

            public void onStart() {

                handler.post(() -> liveTranscript.setValue("正在录音…"));

            }



            @Override

            public void onVolumeChanged(int volume) {

                if (volume > 5) {

                    handler.post(() -> liveTranscript.setValue("正在聆听… 音量 " + volume));

                }

            }



            @Override

            public void onPartialResult(String text) {

                if (text != null && !text.isEmpty()) {

                    handler.post(() -> liveTranscript.setValue(text));

                }

            }



            @Override

            public void onFinalResult(String text) {

                handler.post(() -> {

                    isRecording.setValue(false);

                    pendingAppendAfterStop = false;

                    handler.removeCallbacks(finishAfterStop);

                    liveTranscript.setValue("");

                    if (text != null && !text.trim().isEmpty()) {

                        appendSegment(text);

                    } else {

                        voiceFeedback.setValue("未识别到语音，请重试");

                    }

                });

            }



            @Override

            public void onError(String error) {

                handler.post(() -> {

                    isRecording.setValue(false);

                    pendingAppendAfterStop = false;

                    handler.removeCallbacks(finishAfterStop);

                    voiceFeedback.setValue(error);

                });

            }

        });

    }



    public void stopRecording() {

        if (!Boolean.TRUE.equals(isRecording.getValue())) return;

        if (useBaiduAsr && baiduAsr != null && baiduAsr.isRecording()) {

            baiduAsr.stopRecording();

            return;

        }

        if (speechManager != null) {

            speechManager.stopListening();

        }

    }



    /** 松开手指：结束识别并写入结果区（微信式松手出字）。 */

    public void stopRecordingAndAppend() {

        if (!Boolean.TRUE.equals(isRecording.getValue())) return;

        pendingAppendAfterStop = true;

        if (useBaiduAsr && baiduAsr != null && baiduAsr.isRecording()) {

            baiduAsr.stopRecording();

            handler.removeCallbacks(finishAfterStop);

            handler.postDelayed(finishAfterStop, 8000);

            return;

        }

        stopRecording();

        handler.removeCallbacks(finishAfterStop);

        handler.postDelayed(finishAfterStop, 2500);

    }



    private void finishVoiceInputAfterStop() {

        if (!pendingAppendAfterStop) return;

        pendingAppendAfterStop = false;

        isRecording.setValue(false);



        String before = cleanTranscriptForInput(fullTranscript.toString());

        appendFromBuffer();

        liveTranscript.setValue("");

        fullTranscript.setLength(0);



        if (before.isEmpty()) {

            voiceFeedback.setValue("未识别到语音，请按住说话并清晰发音，或点「系统语音输入」");

        }

    }



    private void appendFromBuffer() {

        String transcript = cleanTranscriptForInput(fullTranscript.toString());

        if (!transcript.isEmpty()) {

            appendSegment(transcript);

        }

    }



    private static String cleanTranscriptForInput(String raw) {

        if (raw == null) return "";

        StringBuilder out = new StringBuilder();

        for (String line : raw.split("\n")) {

            String t = line.trim();

            if (t.isEmpty()) continue;

            if (t.startsWith("[") && t.endsWith("]")) continue;

            if (out.length() > 0) out.append('\n');

            out.append(t);

        }

        return out.toString().trim();

    }



    @Subscribe(threadMode = ThreadMode.MAIN)

    public void onAudioTranscribeEvent(AudioTranscribeEvent event) {

        if (useBaiduAsr) return;

        String seg = event.transcribedSegment != null ? event.transcribedSegment : "";

        if (!event.isFinal) {

            if (!seg.isEmpty()) {

                liveTranscript.setValue(fullTranscript + seg);

            }

            return;

        }



        if (!seg.isEmpty()) {

            if (seg.startsWith("[")) {

                liveTranscript.setValue(seg);

                if (pendingAppendAfterStop) {

                    handler.removeCallbacks(finishAfterStop);

                    finishVoiceInputAfterStop();

                } else {

                    isRecording.setValue(false);

                }

                String msg = seg.replace("[", "").replace("]", "");

                voiceFeedback.setValue(msg);

            } else {

                fullTranscript.append(seg);

                liveTranscript.setValue(fullTranscript.toString());

                if (pendingAppendAfterStop) {

                    handler.removeCallbacks(finishAfterStop);

                    finishVoiceInputAfterStop();

                }

            }

        } else if (pendingAppendAfterStop) {

            handler.removeCallbacks(finishAfterStop);

            finishVoiceInputAfterStop();

        } else {

            isRecording.setValue(false);

        }

    }



    @Subscribe(threadMode = ThreadMode.MAIN)

    public void onRecordingStateEvent(RecordingStateEvent event) {

        if (useBaiduAsr) return;

        if (event.state == RecordingStateEvent.State.ERROR) {

            liveTranscript.setValue("[录音错误: " + event.errorMsg + "]");

        }

    }



    @Override

    protected void onCleared() {

        super.onCleared();

        handler.removeCallbacks(finishAfterStop);

        if (speechManager != null) {

            try { speechManager.cancel(); } catch (Exception ignored) {}

            speechManager.destroy();

        }

        if (baiduAsr != null) {

            baiduAsr.release();

            baiduAsr = null;

        }

        EventBus.getDefault().unregister(this);

    }

}

