package com.cloudink.app.ui.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.cloudink.app.R;
import com.cloudink.app.data.local.AppDatabase;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.Tag;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;

/**
 * 首页 Activity — 承载 HomeFragment + 首次启动自动植入演示数据。
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "CloudInk_Demo";
    /** 测试 PDF 保存在 app 外部 files 目录, SplitReader 可直接加载。 */
    public static final String DEMO_PDF_NAME = "cloudink_demo.pdf";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        seedDemoDataIfNeeded();
    }

    // ================================================================
    // 首次启动: 植入演示数据
    // ================================================================

    private void seedDemoDataIfNeeded() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance();

            // 1. Room: 如果无记录, 插入一条演示笔记
            try {
                int count = db.handwriteRecordDao().getCountSync();
                if (count == 0) {
                    insertDemoRecords(db);
                    Log.i(TAG, "Demo records inserted into Room");
                } else {
                    Log.i(TAG, "Room already has " + count + " records, skip seeding");
                }
            } catch (Exception e) {
                Log.w(TAG, "Room count query failed: " + e.getMessage());
                // 回退: 直接插入 (onConflict=REPLACE 保证不重复插入)
                insertDemoRecords(db);
            }

            // 2. PDF: 如果测试 PDF 不存在, 用 PdfDocument 生成一份
            File demoPdf = new File(getExternalFilesDir(null), DEMO_PDF_NAME);
            if (!demoPdf.exists()) {
                generateDemoPdf(demoPdf);
                Log.i(TAG, "Demo PDF generated: " + demoPdf.getAbsolutePath());
            } else {
                Log.i(TAG, "Demo PDF already exists");
            }
        });
    }

    /** 插入 2 条自带完整排版参数的示范笔记。 */
    private void insertDemoRecords(AppDatabase db) {
        // 笔记 1: 古诗
        HandwriteRecord r1 = new HandwriteRecord();
        r1.setTitle("静夜思 · 李白");
        r1.setContent("静夜思\n\n床前明月光，\n疑是地上霜。\n举头望明月，\n低头思故乡。");
        r1.setPaperIndex(1);       // 横线纸
        r1.setPenType("fountain"); // 钢笔
        r1.setCharSpacing(0.5f);
        r1.setLineSpacing(1.6f);
        r1.setJitterThreshold(0.35f);
        r1.setFontPath("fonts/NiHeWoDeLangManYuZhou-2.ttf");
        r1.setFolderName("古诗");

        // 笔记 2: 云墨介绍
        HandwriteRecord r2 = new HandwriteRecord();
        r2.setTitle("云墨 CloudInk — 项目介绍");
        r2.setContent("云墨 (CloudInk) 是一款基于 Android 原生技术栈开发的"
            + "一站式多模态手写笔记工具。\n\n"
            + "核心功能:\n"
            + "1. 离线 OCR 文字提取 (Google ML Kit)\n"
            + "2. 语音实时转写 (SpeechRecognizer)\n"
            + "3. 手写模拟渲染 (Canvas 抖动算法)\n"
            + "4. 分屏文档阅读 (原生 PdfRenderer)\n"
            + "5. 防 OOM 高清导出 (A4 300dpi)\n\n"
            + "项目采用 MVVM 架构, Room 数据库, DataBinding 双向绑定, "
            + "EventBus 组件通信。全部核心功能使用 Android 系统原生 API 实现, "
            + "零第三方 PDF 库依赖。");
        r2.setPaperIndex(0);        // 纯白纸
        r2.setPenType("ballpoint"); // 圆珠笔
        r2.setCharSpacing(0.4f);
        r2.setLineSpacing(1.4f);
        r2.setJitterThreshold(0.25f);
        r2.setFontPath("fonts/YuShengWenRouDuGeiNi-2.ttf");
        r2.setFolderName("项目");

        // 标签
        Tag t1 = new Tag();
        t1.setName("古诗");
        t1.setColor(0xFF1A73E8);
        Tag t2 = new Tag();
        t2.setName("演示");
        t2.setColor(0xFFFF5722);

        db.runInTransaction(() -> {
            db.handwriteRecordDao().insert(r1);
            db.handwriteRecordDao().insert(r2);
            db.tagDao().insert(t1);
            db.tagDao().insert(t2);
        });
    }

    /** 使用 Android 原生 PdfDocument 生成一份演示 PDF, 包含中英文混排文本。 */
    private void generateDemoPdf(File output) {
        PdfDocument pdf = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 标题
        paint.setTextSize(24f);
        paint.setFakeBoldText(true);
        paint.setColor(0xFF1A237E);
        canvas.drawText("云墨 CloudInk — 演示文档", 60, 60, paint);

        // 正文
        paint.setTextSize(14f);
        paint.setFakeBoldText(false);
        paint.setColor(0xFF333333);

        String[] lines = {
            "",
            "本 PDF 由云墨 App 内置的 PdfDocument 引擎自动生成。",
            "",
            "一、项目简介",
            "云墨 (CloudInk) 是一款面向数字化学习与无纸化办公场景",
            "的现代 Android 应用。它深度融合了端侧离线 OCR、语音实时转写、",
            "防 OOM 分块手写渲染引擎和原生 PDF 逐页浏览。",
            "",
            "二、核心技术",
            "1. Google ML Kit 离线 OCR —— 端侧识别, 零网络延迟",
            "2. SpeechRecognizer 实时转写 —— 边录边出字",
            "3. Canvas 手写渲染 —— 高斯抖动算法, 模拟真实笔迹",
            "4. PdfRenderer 逐页渲染 —— 零第三方 PDF 库",
            "",
            "三、使用指南",
            "长按左侧页面任意位置 → 弹出摘录对话框 → 输入文字",
            "→ 点击'摘录到草稿板' → 文字通过 EventBus 发送至右侧草稿板。",
            "",
            "四、注意事项",
            "本 App 获得 Apache 2.0 + MIT 双重开源许可证兼容审查。",
            "手写抖动算法参考 saurabhdaware/text-to-handwriting (MIT)。",
            "",
            "祝答辩顺利!   — CloudInk 开发团队, 2026年5月",
        };

        float y = 110;
        float lineHeight = 28f;
        for (String line : lines) {
            canvas.drawText(line, 60, y, paint);
            y += lineHeight;
        }

        // 分隔线
        paint.setColor(0xFFBDBDBD);
        paint.setStrokeWidth(1f);
        canvas.drawLine(60, y - 10, 535, y - 10, paint);

        pdf.finishPage(page);

        try (FileOutputStream fos = new FileOutputStream(output)) {
            pdf.writeTo(fos);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write demo PDF: " + e.getMessage());
        } finally {
            pdf.close();
        }
    }
}
