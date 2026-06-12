package com.cloudink.app.rendering;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * 支持双指缩放 + 拖拽的 ImageView。
 * 初始状态以 fitCenter 方式居中显示整张图，保证导出预览能看到完整内容。
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 8.0f;

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private ScaleGestureDetector scaleDetector;

    private float lastTouchX;
    private float lastTouchY;
    private int activePointers = 0;
    private boolean initialized = false;

    public ZoomableImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed || !initialized) {
            fitImageToView();
        }
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        initialized = false;
        post(this::fitImageToView);
    }

    /** 将图片以 fitCenter 方式居中缩放到 View 范围内。 */
    private void fitImageToView() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0)
            return;

        int drawW = d.getIntrinsicWidth();
        int drawH = d.getIntrinsicHeight();
        if (drawW <= 0 || drawH <= 0)
            return;

        float viewW = getWidth();
        float viewH = getHeight();

        float scaleX = viewW / drawW;
        float scaleY = viewH / drawH;
        float scale = Math.min(scaleX, scaleY);

        float scaledW = drawW * scale;
        float scaledH = drawH * scale;
        float dx = (viewW - scaledW) / 2f;
        float dy = (viewH - scaledH) / 2f;

        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
        initialized = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                activePointers++;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && activePointers == 1) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    matrix.postTranslate(dx, dy);
                    clampTranslation();
                    setImageMatrix(matrix);
                }
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activePointers > 0)
                    activePointers--;
                break;
        }
        return true;
    }

    /** 限制平移范围，避免图片被拖出屏幕太多。 */
    private void clampTranslation() {
        Drawable d = getDrawable();
        if (d == null)
            return;

        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        float scale = matrixValues[Matrix.MSCALE_X];

        float scaledW = d.getIntrinsicWidth() * scale;
        float scaledH = d.getIntrinsicHeight() * scale;
        float viewW = getWidth();
        float viewH = getHeight();

        float minX = scaledW > viewW ? viewW - scaledW : 0;
        float maxX = scaledW > viewW ? 0 : viewW - scaledW;
        float minY = scaledH > viewH ? viewH - scaledH : 0;
        float maxY = scaledH > viewH ? 0 : viewH - scaledH;

        float dx = 0, dy = 0;
        if (transX < minX)
            dx = minX - transX;
        if (transX > maxX)
            dx = maxX - transX;
        if (transY < minY)
            dy = minY - transY;
        if (transY > maxY)
            dy = maxY - transY;

        if (dx != 0 || dy != 0) {
            matrix.postTranslate(dx, dy);
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            matrix.getValues(matrixValues);
            float currentScale = matrixValues[Matrix.MSCALE_X];
            float factor = detector.getScaleFactor();
            float newScale = currentScale * factor;

            if (newScale < MIN_SCALE)
                factor = MIN_SCALE / currentScale;
            if (newScale > MAX_SCALE)
                factor = MAX_SCALE / currentScale;

            matrix.postScale(factor, factor,
                    detector.getFocusX(), detector.getFocusY());
            clampTranslation();
            setImageMatrix(matrix);
            return true;
        }
    }
}
