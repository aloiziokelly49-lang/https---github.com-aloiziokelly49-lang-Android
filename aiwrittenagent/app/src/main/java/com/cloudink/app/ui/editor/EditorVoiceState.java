package com.cloudink.app.ui.editor;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.cloudink.app.BR;

/** 手写页悬浮语音面板 —— DataBinding 双向绑定状态。 */

//使用 BaseObservable 和 @Bindable 注解
//把转写后的文本双向绑定到 UI，
public class EditorVoiceState extends BaseObservable {

    private boolean recording;
    private boolean panelExpanded;
    private boolean transcribing;
    private String liveTranscript = "";
    private String statusMessage = "";

    @Bindable
    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        if (this.recording != recording) {
            this.recording = recording;
            // 录音状态变化时通知 UI 更新
            notifyPropertyChanged(BR.recording);
        }
    }

    @Bindable
    public boolean isPanelExpanded() {
        return panelExpanded;
    }

    public void setPanelExpanded(boolean panelExpanded) {
        if (this.panelExpanded != panelExpanded) {
            this.panelExpanded = panelExpanded;
            notifyPropertyChanged(BR.panelExpanded);
        }
    }

    @Bindable
    public boolean isTranscribing() {
        return transcribing;
    }

    public void setTranscribing(boolean transcribing) {
        if (this.transcribing != transcribing) {
            this.transcribing = transcribing;
            // 转写状态变化时通知 UI 更新
            notifyPropertyChanged(BR.transcribing);
            refreshAppendEnabled();
        }
    }

    @Bindable
    public String getLiveTranscript() {
        return liveTranscript;
    }

    public void setLiveTranscript(String liveTranscript) {
        String next = liveTranscript != null ? liveTranscript : "";
        if (!next.equals(this.liveTranscript)) {
            this.liveTranscript = next;
            // 转写文本变化时通知 UI 更新
            notifyPropertyChanged(BR.liveTranscript);
            refreshAppendEnabled();
        }
    }

    @Bindable
    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        String next = statusMessage != null ? statusMessage : "";
        if (!next.equals(this.statusMessage)) {
            this.statusMessage = next;
            // 转写状态消息变化时通知 UI 更新
            notifyPropertyChanged(BR.statusMessage);
        }
    }

    @Bindable
    public boolean isAppendEnabled() {
        return liveTranscript != null && !liveTranscript.trim().isEmpty() && !transcribing;
    }

    public void refreshAppendEnabled() {
        notifyPropertyChanged(BR.appendEnabled);
    }
}
