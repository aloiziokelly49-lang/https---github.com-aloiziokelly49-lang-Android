package com.cloudink.app.rendering.model;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 纸张配色主题管理器 — 四色纸背景切换。
 *
 * <p>通过 {@link SharedPreferences} 持久化当前主题索引。
 * 每个主题定义: 纸底色 / 预览画布底色 / 横线颜色 / 主题名。
 */
public enum PaperThemeManager {

    /** 护眼黄 — 经典米黄纸 */
    EYE_CARE(0, 0xFFF5F0E8, 0xFFF0EDE4, 0x6087CEEB, "护眼黄"),

    /** 极简白 — 纯白底 */
    MINIMAL_WHITE(1, 0xFFFFFFFF, 0xFFF8F8F8, 0x60BDBDBD, "极简白"),

    /** 深邃黑 — 深色纸 (黑底白字适配)*/
    DEEP_DARK(2, 0xFF2A2A2A, 0xFF1E1E1E, 0x40555555, "深邃黑"),

    /** 简约灰 — 中性灰底 */
    NEUTRAL_GRAY(3, 0xFFE8E8E8, 0xFFDCDCDC, 0x60A0A0A0, "简约灰");

    private static final String PREF_KEY = "paper_theme_index";

    private final int index;
    private final int paperColor;   // 纸面底色 (用于 Bitmap 渲染)
    private final int canvasColor;  // 预览画布底色 (PreviewCanvas)
    private final int lineColor;    // 横线/方格颜色
    private final String label;     // 中文名

    PaperThemeManager(int index, int paperColor, int canvasColor, int lineColor, String label) {
        this.index = index;
        this.paperColor = paperColor;
        this.canvasColor = canvasColor;
        this.lineColor = lineColor;
        this.label = label;
    }

    public int getPaperColor()  { return paperColor; }
    public int getCanvasColor() { return canvasColor; }
    public int getLineColor()   { return lineColor; }
    public String getLabel()    { return label; }

    /** 根据 index 获取主题, 越界回退到 EYE_CARE。 */
    public static PaperThemeManager fromIndex(int idx) {
        for (PaperThemeManager t : values()) {
            if (t.index == idx) return t;
        }
        return EYE_CARE;
    }

    /** 持久化当前主题索引。 */
    public static void saveTheme(Context ctx, int index) {
        ctx.getSharedPreferences("cloudink_theme", Context.MODE_PRIVATE)
            .edit().putInt(PREF_KEY, index).apply();
    }

    /** 读取持久化的主题索引。 */
    public static int loadThemeIndex(Context ctx) {
        return ctx.getSharedPreferences("cloudink_theme", Context.MODE_PRIVATE)
            .getInt(PREF_KEY, 0);
    }
}
