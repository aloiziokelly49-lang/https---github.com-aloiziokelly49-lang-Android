package com.cloudink.app.ui.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import androidx.databinding.Observable;
import androidx.databinding.ObservableField;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cloudink.app.CloudInkApplication;
import com.cloudink.app.asr.BaiduAsrConfig;
import com.cloudink.app.asr.SpeechInputHelper;
import com.cloudink.app.rendering.HandwritingEngine;
import com.cloudink.app.rendering.model.HandwritingParams;
import com.cloudink.app.rendering.model.PenType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 手写编辑器 ViewModel —— 参数模型 + 渲染引擎。 */
public class HandwriteEditorViewModel extends ViewModel {

    private final HandwritingEngine engine;
    private final HandwritingParams params;
    /** 排版草稿文本，与输入框双向绑定。 */
    public final ObservableField<String> inputText = new ObservableField<>("");

    private final EditorVoiceState voiceState = new EditorVoiceState();
    private final EditorVoiceCaptureController voiceCapture;

    private final MutableLiveData<Bitmap> renderedBitmap = new MutableLiveData<>();
    private final ExecutorService renderExecutor;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private final Runnable renderTask = new RenderTask();
    private static final int DEBOUNCE_MS = 200;
    private int renderWidth = 1080;

    public HandwriteEditorViewModel() {
        this.engine = CloudInkApplication.getInstance().getHandwritingEngine();
        this.params = new HandwritingParams();
        this.renderExecutor = Executors.newSingleThreadExecutor();
        loadPreferencesIntoParams();
        inputText.set(getDefaultText());

        params.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable sender, int propertyId) {
                onParamsChanged();
            }
        });

        voiceCapture = new EditorVoiceCaptureController(
            CloudInkApplication.getInstance(), voiceState);
        voiceCapture.register();
        initVoiceStatusHint();

        scheduleRender();
    }

    public MutableLiveData<Bitmap> getRenderedBitmap() { return renderedBitmap; }
    public HandwritingParams getParams() { return params; }
    public EditorVoiceState getVoiceState() { return voiceState; }

    public String getInputText() {
        String v = inputText.get();
        return v != null ? v : "";
    }

    public void setInputText(String text) {
        String next = (text == null || text.trim().isEmpty()) ? "" : text;
        inputText.set(next);
        scheduleRender();
    }

    private void loadPreferencesIntoParams() {
        com.cloudink.app.data.preferences.AppPreferences prefs =
            CloudInkApplication.getInstance().getPreferences();
        params.setCharSpacing(prefs.getDefaultCharSpacing());
        params.setLineSpacing(prefs.getDefaultLineSpacing());
        params.setJitterThreshold(prefs.getDefaultJitter());
        params.setPaperIndex(prefs.getDefaultPaperIndex());
        params.setPenType(prefs.getDefaultPenType());
        String font = prefs.getDefaultFontPath();
        if (font != null && !font.isEmpty()) {
            params.setFontPath(font);
            engine.switchFont(CloudInkApplication.getInstance(), font);
        }
    }

    private void initVoiceStatusHint() {
        Context ctx = CloudInkApplication.getInstance();
        if (BaiduAsrConfig.isConfigured()) {
            voiceState.setStatusMessage("点麦克风开始：后台录音 + 百度/系统实时转写");
        } else if (SpeechInputHelper.hasStreamingRecognizer(ctx)) {
            voiceState.setStatusMessage("点麦克风开始：系统边录边转写（后台 Service 保活）");
        } else {
            voiceState.setStatusMessage(SpeechInputHelper.imeVoiceHint(ctx));
        }
    }

    public void startVoiceCapture() {
        voiceCapture.startCapture();
    }

    public void stopVoiceCapture() {
        voiceCapture.stopCapture();
    }

    public void interruptVoiceCapture() {
        voiceCapture.interruptCapture();
    }

    public void cancelVoiceCapture() {
        voiceCapture.cancelCapture();
        initVoiceStatusHint();
    }

    public void appendVoiceToDraft() {
        String segment = voiceState.getLiveTranscript();
        if (segment == null || segment.trim().isEmpty()) return;

        String cleaned = segment.trim();
        String current = getInputText();
        if (!current.isEmpty() && !current.endsWith("\n")) {
            current += "\n";
        }
        setInputText(current + cleaned);
        voiceState.setLiveTranscript("");
        voiceCapture.clearSession();
        voiceState.refreshAppendEnabled();
        scheduleRender();
    }

    public void appendExternalVoiceText(String text) {
        voiceCapture.appendExternalSegment(text);
        voiceState.setPanelExpanded(true);
    }

    public void setVoicePanelExpanded(boolean expanded) {
        voiceState.setPanelExpanded(expanded);
    }

    public boolean isVoiceCapturing() {
        return voiceCapture.isCapturing();
    }

    public PenType getCurrentPenType() {
        try { return PenType.valueOf(params.getPenType().toUpperCase()); }
        catch (IllegalArgumentException e) { return PenType.FOUNTAIN; }
    }

    public void setPenType(String name) { params.setPenType(name.toLowerCase()); }
    public void setPaperIndex(int idx) { params.setPaperIndex(idx); }
    public void requestRender() { scheduleRender(); }

    public void setRenderWidth(int widthPx) {
        if (widthPx > 0) {
            this.renderWidth = widthPx;
            scheduleRender();
        }
    }

    private void onParamsChanged() { scheduleRender(); }

    private void scheduleRender() {
        debounceHandler.removeCallbacks(renderTask);
        debounceHandler.postDelayed(renderTask, DEBOUNCE_MS);
    }

    private class RenderTask implements Runnable {
        @Override
        public void run() {
            final String text = getInputText();
            if (text == null || text.isEmpty()) {
                renderedBitmap.postValue(null);
                return;
            }

            final HandwritingParams snapshot = new HandwritingParams();
            snapshot.setTextSize(params.getTextSize());
            snapshot.setCharSpacing(params.getCharSpacing());
            snapshot.setLineSpacing(params.getLineSpacing());
            snapshot.setPaperIndex(params.getPaperIndex());
            snapshot.setFontPath(params.getFontPath());
            snapshot.setPenType(params.getPenType());

            final PenType penType;
            try {
                penType = PenType.valueOf(snapshot.getPenType().toUpperCase());
            } catch (IllegalArgumentException e) {
                renderedBitmap.postValue(null);
                return;
            }

            renderExecutor.submit(() -> {
                if (snapshot.getFontPath() != null && !snapshot.getFontPath().isEmpty()) {
                    engine.switchFont(CloudInkApplication.getInstance(), snapshot.getFontPath());
                }
                int w = Math.max(renderWidth, 480);
                Bitmap bmp = engine.render(text, snapshot, penType, w);
                renderedBitmap.postValue(bmp);
            });
        }
    }

    private static String getDefaultText() {
        return "云墨 CloudInk\n"
            + "这是一款基于 Android 原生的手写模拟工具。\n\n"
            + "静夜思\n"
            + "床前明月光，\n"
            + "疑是地上霜。\n"
            + "举头望明月，\n"
            + "低头思故乡。\n\n"
            + "2024 年 6 月 15 日";
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        voiceCapture.release();
        debounceHandler.removeCallbacks(renderTask);
        renderExecutor.shutdownNow();
        Bitmap last = renderedBitmap.getValue();
        if (last != null && !last.isRecycled()) last.recycle();
    }
}
