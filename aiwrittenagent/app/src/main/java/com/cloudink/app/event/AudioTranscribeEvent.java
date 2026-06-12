package com.cloudink.app.event;

/** Posted by AudioRecorderService when a new transcription segment is available. */
public class AudioTranscribeEvent {
    public final String transcribedSegment;
    public final boolean isFinal;

    public AudioTranscribeEvent(String transcribedSegment, boolean isFinal) {
        this.transcribedSegment = transcribedSegment;
        this.isFinal = isFinal;
    }
}
