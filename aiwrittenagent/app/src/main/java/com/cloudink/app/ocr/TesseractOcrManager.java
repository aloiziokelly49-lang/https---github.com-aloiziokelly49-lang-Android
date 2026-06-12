package com.cloudink.app.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tesseract OCR（开源离线，支持简体中文）。
 *
 * <p>语言包已内置在 {@code assets/tessdata/chi_sim.traineddata}，
 * 首次使用时复制到应用私有目录，无需联网下载。
 */
public class TesseractOcrManager {

    private static final String TAG = "TesseractOcr";
    private static final String LANG = "chi_sim";
    private static final String ASSET_PATH = "tessdata/chi_sim.traineddata";
    private static final long MIN_TRAINEDDATA_BYTES = 1_000_000L;
    /** 识别前最长边，过大图会极慢且易乱码 */
    private static final int MAX_RECOGNIZE_EDGE = 1280;

    private static volatile boolean assetsCopyDone;

    private final TessBaseAPI tessApi;
    private final Context context;
    private boolean initialized;

    public TesseractOcrManager(Context context) {
        this.context = context.getApplicationContext();
        this.tessApi = new TessBaseAPI();
        ensureLanguageData();
    }

    public static void prepareAsync(Context context) {
        if (assetsCopyDone) return;
        new Thread(() -> {
            TesseractOcrManager mgr = new TesseractOcrManager(context);
            Log.i(TAG, "OCR prepare: " + (mgr.initialized ? "ready" : "not ready"));
            mgr.close();
        }, "tess-prepare").start();
    }

    private File getDataRoot() {
        return new File(context.getFilesDir(), "tesseract");
    }

    private File getTrainedDataFile() {
        return new File(getDataRoot(), "tessdata/" + LANG + ".traineddata");
    }

    private synchronized void ensureLanguageData() {
        File trained = getTrainedDataFile();
        if (!trained.exists() || trained.length() < MIN_TRAINEDDATA_BYTES) {
            copyFromAssets();
        }
        if (trained.exists() && trained.length() >= MIN_TRAINEDDATA_BYTES) {
            initTesseract();
        } else {
            initialized = false;
            Log.e(TAG, "Language data missing at " + trained.getAbsolutePath());
        }
    }

    private void copyFromAssets() {
        File trained = getTrainedDataFile();
        File tessdataDir = trained.getParentFile();
        if (tessdataDir != null && !tessdataDir.exists() && !tessdataDir.mkdirs()) {
            Log.e(TAG, "Cannot create tessdata dir");
            return;
        }

        try (InputStream in = context.getAssets().open(ASSET_PATH);
             FileOutputStream out = new FileOutputStream(trained)) {
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
            }
            assetsCopyDone = true;
            Log.i(TAG, "Copied language data from assets (" + (total / 1024) + " KB)");
        } catch (IOException e) {
            Log.e(TAG, "Copy from assets failed: " + e.getMessage());
            if (trained.exists()) trained.delete();
        }
    }

    private void initTesseract() {
        try {
            String dataPath = getDataRoot().getAbsolutePath();
            if (!dataPath.endsWith("/")) dataPath += "/";
            boolean ok = tessApi.init(dataPath, LANG);
            if (ok) {
                tessApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
                tessApi.setVariable(TessBaseAPI.VAR_SAVE_BLOB_CHOICES, "F");
            }
            initialized = ok;
            Log.i(TAG, "Tesseract init " + (ok ? "success" : "failed") + " path=" + dataPath);
        } catch (Exception e) {
            initialized = false;
            Log.e(TAG, "Tesseract init error: " + e.getMessage());
        }
    }

    public String recognize(Bitmap bitmap) {
        return recognizeInternal(bitmap, TessBaseAPI.PageSegMode.PSM_AUTO, false);
    }

    /** 本地回退：针对手写/稀疏文字优化预处理与分页模式。 */
    public String recognizeHandwriting(Bitmap bitmap) {
        return recognizeInternal(bitmap, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT, true);
    }

    private String recognizeInternal(Bitmap bitmap, int mode, boolean handwriting) {
        if (bitmap == null || bitmap.isRecycled()) return "";

        if (!initialized) {
            ensureLanguageData();
            if (!initialized) {
                return "[OCR 未就绪：语言包复制失败，请重新安装应用]";
            }
        }

        Bitmap scaled = handwriting ? scaleForHandwriting(bitmap) : scaleForOcr(bitmap);
        Bitmap work = handwriting ? preprocessForHandwriting(scaled) : preprocessForOcr(scaled);
        boolean recycleScaled = (scaled != bitmap);
        boolean recycleWork = (work != scaled);
        try {
            tessApi.setPageSegMode(mode);
            tessApi.setImage(work);
            String text = tessApi.getUTF8Text();
            tessApi.clear();
            tessApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            return postProcess(text);
        } catch (Exception e) {
            Log.w(TAG, "Recognition error: " + e.getMessage());
            return "[识别失败: " + e.getMessage() + "]";
        } finally {
            if (recycleWork && work != null && !work.isRecycled()) {
                work.recycle();
            }
            if (recycleScaled && scaled != null && !scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    private static Bitmap scaleForHandwriting(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int maxEdge = Math.max(w, h);
        int target = 1920;
        if (maxEdge >= target && maxEdge <= 2560) return src;
        float scale = maxEdge < target
            ? (float) target / maxEdge
            : (float) 2560 / maxEdge;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        return Bitmap.createBitmap(src, 0, 0, w, h, m, true);
    }

    public static Bitmap preprocessForHandwriting(Bitmap src) {
        Bitmap contrast = preprocessForOcr(src);
        int w = contrast.getWidth();
        int h = contrast.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        ColorMatrix sharpen = new ColorMatrix(new float[]{
            1.6f, 0, 0, 0, -40f,
            0, 1.6f, 0, 0, -40f,
            0, 0, 1.6f, 0, -40f,
            0, 0, 0, 1f, 0f
        });
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(sharpen));
        canvas.drawBitmap(contrast, 0, 0, paint);
        if (contrast != src && contrast != out) contrast.recycle();
        return out;
    }

    /** 灰度 + 对比度，提升印刷体/截图识别率。 */
    public static Bitmap preprocessForOcr(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        ColorMatrix gray = new ColorMatrix();
        gray.setSaturation(0f);
        ColorMatrix contrast = new ColorMatrix(new float[]{
            1.35f, 0, 0, 0, -24f,
            0, 1.35f, 0, 0, -24f,
            0, 0, 1.35f, 0, -24f,
            0, 0, 0, 1f, 0f
        });
        gray.postConcat(contrast);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(gray));
        canvas.drawBitmap(src, 0, 0, paint);
        return out;
    }

    /** 缩放到合理尺寸，显著加快识别并减少乱码。 */
    public static Bitmap scaleForOcr(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int maxEdge = Math.max(w, h);
        if (maxEdge <= MAX_RECOGNIZE_EDGE) return src;

        float scale = (float) MAX_RECOGNIZE_EDGE / maxEdge;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        return Bitmap.createBitmap(src, 0, 0, w, h, m, true);
    }

    /** 解码时按目标最长边计算 inSampleSize。 */
    public static int computeInSampleSize(int width, int height, int maxEdge) {
        int maxDim = Math.max(width, height);
        int sample = 1;
        while (maxDim / sample > maxEdge * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static String postProcess(String text) {
        if (text == null) return "";
        text = text.trim();
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text;
    }

    public boolean isReady() {
        return initialized;
    }

    public void close() {
        try {
            tessApi.end();
        } catch (Exception ignored) {
        }
    }
}
