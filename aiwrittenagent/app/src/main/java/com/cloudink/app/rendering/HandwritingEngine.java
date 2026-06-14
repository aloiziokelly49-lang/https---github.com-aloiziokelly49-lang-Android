package com.cloudink.app.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;

import com.cloudink.app.rendering.model.HandwritingParams;
import com.cloudink.app.rendering.model.PaperThemeManager;
import com.cloudink.app.rendering.model.PenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 云墨 (CloudInk) — 手写字体渲染引擎。
 *
 * 使用真实 .ttf 手写字体进行 Canvas 逐行排版渲染，
 * 保留用户选择的字体原貌，不做额外变形处理。
 */
public class HandwritingEngine {

    private static final String TAG = "HandwritingEngine";
    private static final String DEFAULT_FONT = "fonts/NiHeWoDeLangManYuZhou-2.ttf";

    private static final int PADDING_H = 40;
    private static final int PADDING_V = 36;
    private static final int LINE_INTERVAL = 28;

    private Typeface handwritingTypeface;
    private boolean fontLoaded;
    private String currentFontPath = DEFAULT_FONT;

    private final Paint textPaint;
    private final Paint paperPaint;
    private final Paint linePaint;

    private PaperThemeManager theme = PaperThemeManager.EYE_CARE;

    public HandwritingEngine() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setStyle(Paint.Style.FILL);

        paperPaint = new Paint();
        paperPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1f);
    }

    public void initFont(Context context) {
        loadFont(context, DEFAULT_FONT);
    }

    public void switchFont(Context context, String fontAssetPath) {
        if (fontAssetPath == null || fontAssetPath.equals(currentFontPath)) return;
        loadFont(context, fontAssetPath);
    }

    public String getCurrentFontPath() {
        return currentFontPath;
    }

    private void loadFont(Context context, String assetPath) {
        try {
            handwritingTypeface = Typeface.createFromAsset(context.getAssets(), assetPath);
            fontLoaded = true;
            currentFontPath = assetPath;
            Log.i(TAG, "Handwriting font loaded: " + assetPath);
        } catch (Exception e) {
            handwritingTypeface = Typeface.MONOSPACE;
            fontLoaded = false;
            Log.w(TAG, "Failed to load " + assetPath + ", using MONOSPACE fallback.");
        }
        textPaint.setTypeface(handwritingTypeface);
    }

    public static List<String> getAvailableFonts(Context context) {
        List<String> fonts = new ArrayList<>();
        try {
            String[] list = context.getAssets().list("fonts");
            if (list != null) {
                for (String name : list) {
                    String lower = name.toLowerCase();
                    if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) continue;
                    if (lower.contains("barlow") || lower.contains("roboto")
                        || lower.contains("arial") || lower.contains("sans")) {
                        continue;
                    }
                    fonts.add("fonts/" + name);
                }
            }
        } catch (java.io.IOException e) {
            Log.w(TAG, "Failed to list fonts assets: " + e.getMessage());
        }
        return fonts;
    }

    public static String getFontDisplayName(String fontPath) {
        if (fontPath == null) return "默认";
        String name = fontPath.substring(fontPath.lastIndexOf('/') + 1);
        name = name.replaceFirst("-[0-9]+\\.(ttf|otf)$", "");
        name = name.replaceFirst("\\.(ttf|otf)$", "");
        switch (name) {
            case "NiHeWoDeLangManYuZhou":     return "你和我的浪漫宇宙";
            case "YuShengWenRouDuGeiNi":       return "余生温柔都给你";
            case "YuZhouBaiRiMeng":            return "宇宙白日梦";
            case "YunDuoAiChiMianHuaTang":     return "云朵爱吃棉花糖";
            case "ZuiShenDeYeLiZuiWenRou":     return "最深的夜里最温柔";
            default:                           return name;
        }
    }

    public void setTheme(PaperThemeManager t) {
        this.theme = t;
    }

    public PaperThemeManager getTheme() {
        return theme;
    }

    // ================================================================
    // 渲染入口 — 字距/行距 + 高斯抖动模拟手写
    // ================================================================

    public Bitmap render(String text, HandwritingParams params,
                         PenType penType, int paperWidth) {
        if (text == null || text.isEmpty()) {
            return createEmptyBitmap(paperWidth, 200);
        }

        // 根据 PenType 设置画笔属性，模拟不同笔触效果
        penType.applyTo(textPaint);
        if (theme == PaperThemeManager.DEEP_DARK) {
            textPaint.setColor(0xFFE8E8E8);
        }
        if (handwritingTypeface != null) {
            textPaint.setTypeface(handwritingTypeface);
        }
        // 设置文本大小
        textPaint.setTextSize(params.getTextSize());

        //按照可用宽度、字符间距和行距进行文本排版，生成每行的文本列表
        List<String> lines = layoutLines(text, params, paperWidth - PADDING_H * 2);

        float lineHeight = params.getLineSpacing() * params.getTextSize();
        
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baselineOffset = -fm.top; // 从顶部到基线的距离
        
        // 计算总图像高度：行高 * 行数 + 上下内边距 + 基线偏移
        int totalHeight = (int) (lines.size() * lineHeight) + PADDING_V * 2 + (int)baselineOffset + PADDING_V;
        // 创建目标 Bitmap 和 Canvas，准备绘制
        Bitmap bitmap = Bitmap.createBitmap(paperWidth, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 绘制纸张背景
        drawPaperBackground(canvas, paperWidth, totalHeight, params.getPaperIndex());

        float cursorY = PADDING_V + baselineOffset;
        float charSpacingPx = params.getCharSpacing() * params.getTextSize();
        //jitter 参数控制抖动强度，范围 0~1，
        // 0 表示无抖动，1 表示最大抖动
        float jitter = Math.max(0f, Math.min(1f, params.getJitterThreshold()));
        
        // 需要为 每个字符 生成一个随机的偏移量，
        // 模拟手写时笔迹的自然抖动

        //然后，需要确保同样文本的自然抖动效果一致
        //因此，需要使用文本内容的哈希值，
        //作为随机数生成器的确定性种子，
        //再生成随机数，
        //那么相同文本产生的种子就相同，抖动效果也就一致了。
        Random rnd = jitter > 0.01f ? new Random(text.hashCode()) : null;
        float jitterScale = params.getTextSize() * 0.12f * jitter;

        for (String line : lines) {
            float cursorX = PADDING_H;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch <= 0x1F && ch != '\t') continue;
                String glyph = String.valueOf(ch);
                float glyphW = textPaint.measureText(glyph);
                float dx = 0f, dy = 0f;
                if (rnd != null) {
                    // 计算高斯抖动偏移，X 轴和 Y 轴的抖动幅度不同，
                    // Y 轴通常更小以保持行间距稳定
                    dx = (rnd.nextFloat() - 0.5f) * 2f * jitterScale;
                    dy = (rnd.nextFloat() - 0.5f) * 2f * jitterScale * 0.6f;
                }
                // 绘制当前字符，应用抖动偏移
                canvas.drawText(glyph, cursorX + dx, cursorY + dy, textPaint);
                cursorX += glyphW + charSpacingPx;
            }
            cursorY += lineHeight;
        }

        return bitmap;
    }

    // ================================================================
    // 纸张背景
    // ================================================================

    private void drawPaperBackground(Canvas canvas, int w, int h, int paperIndex) {
        paperPaint.setColor(theme.getPaperColor());
        canvas.drawRect(0, 0, w, h, paperPaint);

        linePaint.setColor(theme.getLineColor());
        switch (paperIndex) {
            case 1: drawRuledLines(canvas, w, h); break;
            case 2: drawGridLines(canvas, w, h, 56); break;
            case 3: drawDotGrid(canvas, w, h, 56); break;
            default: break;
        }
    }

    private void drawRuledLines(Canvas canvas, int w, int h) {
        for (int y = 0; y < h; y += LINE_INTERVAL) {
            canvas.drawLine(0, y, w, y, linePaint);
        }
    }

    private void drawGridLines(Canvas canvas, int w, int h, int sp) {
        for (int y = 0; y < h; y += sp) canvas.drawLine(0, y, w, y, linePaint);
        for (int x = 0; x < w; x += sp) canvas.drawLine(x, 0, x, h, linePaint);
    }

    private void drawDotGrid(Canvas canvas, int w, int h, int sp) {
        Paint dot = new Paint(linePaint);
        dot.setStyle(Paint.Style.FILL);
        dot.setStrokeWidth(2f);
        for (int y = sp / 2; y < h; y += sp) {
            for (int x = sp / 2; x < w; x += sp) {
                canvas.drawPoint(x, y, dot);
            }
        }
    }

    // ================================================================
    // 文本排版
    // ================================================================

    private List<String> layoutLines(String text, HandwritingParams params, int availableWidth) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { lines.add(""); continue; }
            layoutParagraph(para, lines, params, availableWidth);
        }
        return lines;
    }

    private void layoutParagraph(String para, List<String> out,
                                 HandwritingParams params, int availableWidth) {
        Paint mp = new Paint(textPaint);
        mp.setTextSize(params.getTextSize());
        StringBuilder cur = new StringBuilder();
        float curW = 0;
        for (int i = 0; i < para.length(); i++) {
            char ch = para.charAt(i);
            String glyph = String.valueOf(ch);
            float gw = mp.measureText(glyph);
            if (curW + gw > availableWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
                curW = 0;
            }
            cur.append(ch);
            curW += gw;
        }
        if (cur.length() > 0) out.add(cur.toString());
    }

    private Bitmap createEmptyBitmap(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
        new Canvas(bmp).drawColor(theme.getPaperColor());
        return bmp;
    }

    public void dispose() {}
}
