package com.cloudink.app.rendering.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

import com.cloudink.app.BR;

/**
 * 手写排版参数模型 —— 支持 DataBinding 双向绑定, 
 * 滑块变更时预览实时刷新。
 *
 */


// BaseObservable 允许属性变更时通知 UI 刷新，
// 配合 @Bindable 注解实现双向绑定。
public class HandwritingParams extends BaseObservable {

    /** 字号 (px) */
    private float textSize = 42f;

    /** 字间距系数 */
    private float charSpacing = 0.5f;

    /** 行间距系数 */
    private float lineSpacing = 1.6f;

    /** 抖动强度: 0 = 打印机, 0.3~0.5 = 自然手写, 1.0 = 狂野抖动。 */
    private float jitterThreshold = 0.35f;

    /** 纸张背景索引 (0=空白, 1=横线, 2=方格, 3=点阵...)。 */
    private int paperIndex = 0;

    /** 笔触类型标识 (对应 PenType.name() 小写)。 */
    private String penType = "fountain";

    /** 手写字体路径 (assets 相对路径, 如 "fonts/xxx.ttf")。 */
    private String fontPath = "fonts/NiHeWoDeLangManYuZhou-2.ttf";

    // ========== DataBinding 双向绑定 ==========

    @Bindable
    public float getTextSize() { return textSize; }
    public void setTextSize(float v) {
        if (v < 14f) v = 14f;
        this.textSize = v;

        // 字号变化时，通知UI刷新预览
        notifyPropertyChanged(BR.textSize);
    }

    @Bindable
    public float getCharSpacing() { return charSpacing; }
    public void setCharSpacing(float v) {
        this.charSpacing = Math.max(0f, Math.min(v, 3f));
        notifyPropertyChanged(BR.charSpacing);
    }

    @Bindable
    public float getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(float v) {
        this.lineSpacing = Math.max(0.8f, Math.min(v, 4f));
        notifyPropertyChanged(BR.lineSpacing);
    }

    @Bindable
    public float getJitterThreshold() { return jitterThreshold; }
    public void setJitterThreshold(float v) {
        this.jitterThreshold = Math.max(0f, Math.min(v, 1.5f));
        notifyPropertyChanged(BR.jitterThreshold);
    }

    @Bindable
    public int getPaperIndex() { return paperIndex; }
    public void setPaperIndex(int v) {
        this.paperIndex = v;
        notifyPropertyChanged(BR.paperIndex);
    }

    @Bindable
    public String getPenType() { return penType; }
    public void setPenType(String v) {
        this.penType = v;
        notifyPropertyChanged(BR.penType);
    }

    @Bindable
    public String getFontPath() { return fontPath; }
    public void setFontPath(String v) {
        this.fontPath = v;
        notifyPropertyChanged(BR.fontPath);
    }
}
