package com.cloudink.app.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import com.cloudink.app.rendering.model.PaperThemeManager;

/**
 * 手写预览画布 — FitCenter 居中绘制，支持双指缩放与单指拖动。
 */
public class PreviewCanvas extends View {

    private Bitmap bitmap;
    private final Paint bitmapPaint;
    private final Paint placeholderPaint;
    private final Matrix drawMatrix;
    private int canvasBgColor;

    private float scaleFactor = 1f;
    private final float minScale = 1f;
    private final float maxScale = 5f;
    private float translateX = 0f;
    private float translateY = 0f;
    private boolean invalidatePending;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    public PreviewCanvas(Context context) { this(context, null); }

    public PreviewCanvas(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreviewCanvas(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        placeholderPaint.setTextSize(36f);
        placeholderPaint.setTextAlign(Paint.Align.CENTER);
        drawMatrix = new Matrix();
        updateColors(context);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new PanListener());
    }

    private void updateColors(Context ctx) {
        int themeIdx = PaperThemeManager.loadThemeIndex(ctx);
        PaperThemeManager t = PaperThemeManager.fromIndex(themeIdx);
        canvasBgColor = t.getCanvasColor();
        boolean isDark = (themeIdx == 2);
        placeholderPaint.setColor(isDark ? 0xFFAAAAAA : 0xFF9E9E9E);
    }

    public void refreshTheme(Context ctx) {
        updateColors(ctx);
        invalidate();
    }

    public void setBitmap(@Nullable Bitmap bmp) {
        this.bitmap = bmp;
        resetTransform();
        invalidate();
    }

    private void resetTransform() {
        scaleFactor = 1f;
        translateX = 0f;
        translateY = 0f;
    }

    private void requestRedraw() {
        if (invalidatePending) return;
        invalidatePending = true;
        postOnAnimation(() -> {
            invalidatePending = false;
            invalidate();
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean sc = scaleDetector.onTouchEvent(event);
        boolean gc = gestureDetector.onTouchEvent(event);
        return sc || gc || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(canvasBgColor);

        if (bitmap != null && !bitmap.isRecycled()) {
            drawBitmapScaled(canvas, w, h);
        } else {
            canvas.drawText("预览区域 — 调节下方参数开始渲染", w / 2f, h / 2f, placeholderPaint);
        }
    }

    private void drawBitmapScaled(Canvas canvas, int viewW, int viewH) {
        float bmpW = bitmap.getWidth(), bmpH = bitmap.getHeight();
        float fitScale = Math.min(viewW / bmpW, viewH / bmpH);
        float effectiveScale = fitScale * scaleFactor;

        float baseX = (viewW - bmpW * fitScale) / 2f;
        float baseY = (viewH - bmpH * fitScale) / 2f;
        float offsetX = baseX + translateX + (bmpW * fitScale - bmpW * effectiveScale) / 2f;
        float offsetY = baseY + translateY + (bmpH * fitScale - bmpH * effectiveScale) / 2f;

        drawMatrix.reset();
        drawMatrix.postScale(effectiveScale, effectiveScale);
        drawMatrix.postTranslate(offsetX, offsetY);
        canvas.drawBitmap(bitmap, drawMatrix, bitmapPaint);
    }

    // ================================================================
    // 缩放手势
    // ================================================================

    // 双指缩放时，保持缩放中心不变，
    // 调整平移以抵消缩放引起的位移，
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //计算新的缩放比例，并限制在 minScale 和 maxScale 之间，
            float newScale = scaleFactor * detector.getScaleFactor();
            newScale = Math.max(minScale, Math.min(newScale, maxScale));
            
            //计算缩放比例的变化倍数，
            //调整 translateX 和 translateY，
            //使缩放中心保持不变，
            float scaleChange = newScale / scaleFactor;
            //dector.getFocusX() 就是缩放中心在 View 坐标系中的位置，
            translateX = detector.getFocusX() - scaleChange * (detector.getFocusX() - translateX);
            translateY = detector.getFocusY() - scaleChange * (detector.getFocusY() - translateY);
            scaleFactor = newScale;
            //请求重绘
            requestRedraw();
            return true;
        }
    }

    // ================================================================
    // 拖动手势
    // ================================================================

    private class PanListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            translateX -= dx;
            translateY -= dy;
            requestRedraw();
            return true;
        }
    }
}
