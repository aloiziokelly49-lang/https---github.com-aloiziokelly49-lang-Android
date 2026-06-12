package com.cloudink.app.event;

/** Posted when OCR recognition completes and text should flow to the editor draft. */
public class OcrResultEvent {
    public final String recognizedText;

    public OcrResultEvent(String recognizedText) {
        this.recognizedText = recognizedText;
    }
}
