package com.cloudink.app.rendering.model;

import android.graphics.Paint;

/**
 * 物理笔触类型枚举 —— 定义不同笔种的渲染特性。
 *
 * <p>每种笔触提供独立的:
 * <ul>
 *   <li>基础笔宽 (strokeWidth) — 影响字迹粗细</li>
 *   <li>透明度 (alpha) — 模拟墨水渗透/马克笔半透明</li>
 *   <li>默认颜色 (defaultColor) — 墨水色</li>
 * </ul>
 */
public enum PenType {

    /**
     * 钢笔 — 较细笔迹, 高不透明度, 深蓝黑墨水。
     * 适合正文书写, 笔锋分明。
     */
    FOUNTAIN(3.0f, 220, 0xFF1A237E) {
        @Override
        public void applyTo(Paint paint) {
            paint.setStrokeWidth(strokeWidth);
            paint.setAlpha(alpha);
            paint.setColor(defaultColor);
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
        }
    },

    /**
     * 圆珠笔 — 中等笔迹, 饱满不透明, 纯黑墨水。
     * 适合日常笔记, 笔画均匀一致。
     */
    BALLPOINT(3.8f, 240, 0xFF1A1A1A) {
        @Override
        public void applyTo(Paint paint) {
            paint.setStrokeWidth(strokeWidth);
            paint.setAlpha(alpha);
            paint.setColor(defaultColor);
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
        }
    },

    /**
     * 马克笔 — 较粗笔迹, 半透明, 色彩醒目。
     * 适合标题或重点标注, 笔触有明显的"洇染"感。
     */
    MARKER(5.5f, 160, 0xFFD32F2F) {
        @Override
        public void applyTo(Paint paint) {
            paint.setStrokeWidth(strokeWidth);
            paint.setAlpha(alpha);
            paint.setColor(defaultColor);
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            // 马克笔叠加一层微弱的模糊模拟洇染
            paint.setShadowLayer(1.5f, 0.5f, 0.5f, 0x33000000);
        }
    };

    protected final float strokeWidth;
    protected final int alpha;
    protected final int defaultColor;

    PenType(float strokeWidth, int alpha, int defaultColor) {
        this.strokeWidth = strokeWidth;
        this.alpha = alpha;
        this.defaultColor = defaultColor;
    }

    /** 将该笔触的属性应用到传入的 Paint 对象上。 */
    public abstract void applyTo(Paint paint);

    public float getStrokeWidth() { return strokeWidth; }
    public int   getAlpha()       { return alpha; }
    public int   getDefaultColor(){ return defaultColor; }
}
