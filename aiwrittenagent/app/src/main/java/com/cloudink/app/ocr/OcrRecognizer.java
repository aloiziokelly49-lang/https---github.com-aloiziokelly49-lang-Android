package com.cloudink.app.ocr;

import android.content.Context;
import android.graphics.Bitmap;


/**
 * 统一 OCR：优先百度手写/通用识别，失败则回退 Tesseract 离线。
 */
public final class OcrRecognizer {

    public interface Callback {
        void onSuccess(String text, boolean fromBaidu);
        void onError(String error);
    }

    private OcrRecognizer() {}

    public static void recognize(Context context, Bitmap bitmap, boolean preferHandwriting, Callback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            callback.onError("图片无效");
            return;
        }
        if (BaiduOcrConfig.isConfigured()) {
            BaiduOcrHelper baidu = new BaiduOcrHelper();
            BaiduOcrHelper.OcrCallback baiduCb = new BaiduOcrHelper.OcrCallback() {
                @Override
                public void onSuccess(String text) {
                    callback.onSuccess(text, true);
                }

                @Override
                public void onError(String error) {
                    runTesseractFallback(context, bitmap, preferHandwriting, callback, error);
                }
            };
            if (preferHandwriting) {
                baidu.recognizeHandwriting(bitmap, baiduCb);
            } else {
                baidu.recognizeGeneral(bitmap, baiduCb);
            }
        } else {
            runTesseractFallback(context, bitmap, preferHandwriting, callback, null);
        }
    }

    private static void runTesseractFallback(Context context, Bitmap bitmap,
                                             boolean handwriting, Callback callback,
                                             String baiduFailHint) {
        new Thread(() -> {
            TesseractOcrManager tess = new TesseractOcrManager(context);
            try {
                String text = handwriting
                    ? tess.recognizeHandwriting(bitmap)
                    : tess.recognize(bitmap);
                if (text == null || text.trim().isEmpty() || text.startsWith("[")) {
                    String msg = text != null && text.startsWith("[") ? text
                        : "未识别到文字";
                    if (baiduFailHint != null) {
                        msg = baiduFailHint + "\n本地识别: " + msg;
                    }
                    callback.onError(msg);
                } else {
                    callback.onSuccess(text, false);
                }
            } catch (Exception e) {
                String msg = "识别失败: " + e.getMessage();
                if (baiduFailHint != null) msg = baiduFailHint + "\n" + msg;
                callback.onError(msg);
            } finally {
                tess.close();
            }
        }, "ocr-tess").start();
    }
}
