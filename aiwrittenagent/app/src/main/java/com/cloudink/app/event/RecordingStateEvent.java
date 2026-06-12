package com.cloudink.app.event;

/** Posted by AudioRecorderService to notify UI about recording lifecycle changes. */
public class RecordingStateEvent {

    public enum State { STARTED, STOPPED, ERROR }

    public final State state;
    public final String filePath;   // STOPPED 时返回保存的文件路径
    public final String errorMsg;   // ERROR 时非空

    private RecordingStateEvent(State state, String filePath, String errorMsg) {
        this.state = state;
        this.filePath = filePath;
        this.errorMsg = errorMsg;
    }

    public static RecordingStateEvent started() {
        return new RecordingStateEvent(State.STARTED, null, null);
    }

    public static RecordingStateEvent stopped(String filePath) {
        return new RecordingStateEvent(State.STOPPED, filePath, null);
    }

    public static RecordingStateEvent error(String msg) {
        return new RecordingStateEvent(State.ERROR, null, msg);
    }
}
