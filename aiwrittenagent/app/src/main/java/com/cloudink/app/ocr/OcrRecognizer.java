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

    //优先使用百度 OCR 进行识别，失败后回退到 Tesseract 本地识别，
    public static void recognize(Context context, Bitmap bitmap, boolean preferHandwriting, Callback callback) {
        if (bitmap == null || bitmap.isRecycled()) {
            callback.onError("图片无效");
            return;
        }
        //如果配置了百度 OCR，优先使用百度 OCR 进行识别，
        if (BaiduOcrConfig.isConfigured()) {
            
            //使用 BaiduOcrHelper（自定义） 进行 OCR 识别，识别完成后通过回调返回结果，
            BaiduOcrHelper baidu = new BaiduOcrHelper();
            BaiduOcrHelper.OcrCallback baiduCb = new BaiduOcrHelper.OcrCallback() {
                @Override
                public void onSuccess(String text) {
                    callback.onSuccess(text, true);
                }

                @Override
                public void onError(String error) {
                    //百度 OCR 失败后，回退到 Tesseract 本地识别，
                    runTesseractFallback(context, bitmap, preferHandwriting, callback, error);
                }
            };
            //根据 preferHandwriting 参数决定使用百度手写或通用识别
            if (preferHandwriting) {
                baidu.recognizeHandwriting(bitmap, baiduCb);
            } else {
                baidu.recognizeGeneral(bitmap, baiduCb);
            }
        } else {
            //如果未配置百度 OCR，直接使用 Tesseract 本地识别，
            runTesseractFallback(context, bitmap, preferHandwriting, callback, null);
        }
    }

    private static void runTesseractFallback(Context context, Bitmap bitmap,
                                             boolean handwriting, Callback callback,
                                             String baiduFailHint) {
        new Thread(() -> {
            //使用 TesseractOcrManager（自定义） 进行 OCR 识别，识别完成后通过回调返回结果，
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
