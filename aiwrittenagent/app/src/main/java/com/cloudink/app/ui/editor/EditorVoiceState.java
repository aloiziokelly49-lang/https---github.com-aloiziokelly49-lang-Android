package com.cloudink.app.ui.editor;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.cloudink.app.BR;

/** 手写页悬浮语音面板 —— DataBinding 双向绑定状态。 */
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
