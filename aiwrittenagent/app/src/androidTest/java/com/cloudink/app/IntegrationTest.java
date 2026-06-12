package com.cloudink.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.cloudink.app.data.local.AppDatabase;
import com.cloudink.app.data.local.entity.Draft;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.Tag;
import com.cloudink.app.event.AudioTranscribeEvent;
import com.cloudink.app.rendering.HandwritingEngine;
import com.cloudink.app.rendering.model.HandwritingParams;
import com.cloudink.app.rendering.model.PenType;
import com.cloudink.app.ui.editor.HandwriteEditorActivity;
import com.cloudink.app.ui.export.ExportPreviewActivity;

import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 4 全链路集成测试 — 覆盖核心数据流:
 * <ol>
 *   <li>Room DB: insert → query → params 往返一致</li>
 *   <li>EventBus: AudioTranscribeEvent 发布/订阅</li>
 *   <li>Intent: HandwriteEditorActivity restore extras 与 HistoryFragment 对齐</li>
 *   <li>HandwritingEngine: render 非空, 不崩溃</li>
 *   <li>Entity 默认值: UUID 不为空, 类型正确</li>
 * </ol>
 * 
 * 注意：TextExtractEvent 相关测试已移除，因为功能点4（双栏分屏阅读）已取消
 */
@RunWith(AndroidJUnit4.class)
public class IntegrationTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class).build();
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    // ================================================================
    // T1: Room 完整读写 + 排版参数持久化
    // ================================================================

    @Test
    public void testRoomWriteReadWithParams() {
        // 写入
        HandwriteRecord r = new HandwriteRecord();
        r.setTitle("集成测试笔记");
        r.setContent("验证 Room 全字段读写。");
        r.setPaperIndex(2);
        r.setPenType("ballpoint");
        r.setFolderName("工作");
        r.setCharSpacing(0.8f);
        r.setLineSpacing(2.0f);
        r.setJitterThreshold(0.5f);
        db.handwriteRecordDao().insert(r);

        // 读取
        HandwriteRecord q = db.handwriteRecordDao().getRecordByIdSync(r.getId());
        Assert.assertNotNull("查询结果不应为空", q);
        Assert.assertEquals("标题一致", r.getTitle(), q.getTitle());
        Assert.assertEquals("内容一致", r.getContent(), q.getContent());
        Assert.assertEquals("paperIndex一致", 2, q.getPaperIndex());
        Assert.assertEquals("penType一致", "ballpoint", q.getPenType());
        Assert.assertEquals("文件夹一致", "工作", q.getFolderName());
        Assert.assertEquals("charSpacing一致", 0.8f, q.getCharSpacing(), 0.001f);
        Assert.assertEquals("lineSpacing一致", 2.0f, q.getLineSpacing(), 0.001f);
        Assert.assertEquals("jitterThreshold一致", 0.5f, q.getJitterThreshold(), 0.001f);

        // 标签关联
        Tag tag = new Tag();
        tag.setName("测试标签");
        tag.setColor(0xFFFF5722);
        db.tagDao().insert(tag);
        db.handwriteRecordDao().insertTagCrossRef(
            new com.cloudink.app.data.local.entity.RecordTagCrossRef(r.getId(), tag.getId()));
        int crossCount = db.handwriteRecordDao().countCrossRef(r.getId(), tag.getId());
        Assert.assertEquals("交叉引用应存在", 1, crossCount);
    }

    // ================================================================
    // T2: EventBus — AudioTranscribeEvent
    // ================================================================

    @Test
    public void testEventBusAudioTranscribe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        Object sub = new Object() {
            @org.greenrobot.eventbus.Subscribe(
                threadMode = org.greenrobot.eventbus.ThreadMode.POSTING)
            public void onEvent(AudioTranscribeEvent e) {
                received.set(e.transcribedSegment + "|" + e.isFinal);
                latch.countDown();
            }
        };

        EventBus.getDefault().register(sub);
        EventBus.getDefault().post(new AudioTranscribeEvent("你好世界", true));

        Assert.assertTrue("AudioTranscribeEvent 应在 3s 内收到",
            latch.await(3, TimeUnit.SECONDS));
        Assert.assertEquals("转写文本+isFinal", "你好世界|true", received.get());

        EventBus.getDefault().unregister(sub);
    }

    // ================================================================
    // T3: Intent extras — 历史档案恢复参数常量对齐
    // ================================================================

    @Test
    public void testIntentRestoreExtrasRoundtrip() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
            HandwriteEditorActivity.class);
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_CONTENT, "测试内容");
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_CHAR_SP, 0.6f);
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_LINE_SP, 1.8f);
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_JITTER, 0.4f);
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_PAPER, 1);
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_PEN, "marker");
        intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_FONT, "fonts/test.ttf");

        Assert.assertEquals("EXTRA_RESTORE_CONTENT", "测试内容",
            intent.getStringExtra(HandwriteEditorActivity.EXTRA_RESTORE_CONTENT));
        Assert.assertEquals("EXTRA_RESTORE_CHAR_SP", 0.6f,
            intent.getFloatExtra(HandwriteEditorActivity.EXTRA_RESTORE_CHAR_SP, 0), 0.001f);
        Assert.assertEquals("EXTRA_RESTORE_LINE_SP", 1.8f,
            intent.getFloatExtra(HandwriteEditorActivity.EXTRA_RESTORE_LINE_SP, 0), 0.001f);
        Assert.assertEquals("EXTRA_RESTORE_JITTER", 0.4f,
            intent.getFloatExtra(HandwriteEditorActivity.EXTRA_RESTORE_JITTER, 0), 0.001f);
        Assert.assertEquals("EXTRA_RESTORE_PAPER", 1,
            intent.getIntExtra(HandwriteEditorActivity.EXTRA_RESTORE_PAPER, -1));
        Assert.assertEquals("EXTRA_RESTORE_PEN", "marker",
            intent.getStringExtra(HandwriteEditorActivity.EXTRA_RESTORE_PEN));
        Assert.assertEquals("EXTRA_RESTORE_FONT", "fonts/test.ttf",
            intent.getStringExtra(HandwriteEditorActivity.EXTRA_RESTORE_FONT));

        // ExportActivity extras 同样对应
        Assert.assertEquals("Export EXTRA_TEXT key matches",
            "export_text", ExportPreviewActivity.EXTRA_TEXT);
    }

    // ================================================================
    // T4: HandwritingEngine 基础渲染
    // ================================================================

    @Test
    public void testHandwritingEngineRender() {
        HandwritingEngine engine = new HandwritingEngine();
        HandwritingParams p = new HandwritingParams();
        p.setCharSpacing(0.5f);
        p.setLineSpacing(1.6f);
        p.setJitterThreshold(0.35f);
        p.setPaperIndex(0);
        p.setPenType("fountain");

        Bitmap bmp = engine.render("云墨 CloudInk 集成测试", p, PenType.FOUNTAIN, 720);
        Assert.assertNotNull("渲染结果不应为空", bmp);
        Assert.assertTrue("渲染宽度应>0", bmp.getWidth() > 0);
        Assert.assertTrue("渲染高度应>0", bmp.getHeight() > 0);
        Assert.assertFalse("Bitmap 不应已回收", bmp.isRecycled());

        bmp.recycle();
        engine.dispose();
    }

    // ================================================================
    // T5: Entity 默认值
    // ================================================================

    @Test
    public void testEntityDefaults() {
        HandwriteRecord r = new HandwriteRecord();
        Assert.assertNotNull("UUID 不为空", r.getId());
        Assert.assertEquals("默认 title", "", r.getTitle());
        Assert.assertEquals("默认 penType", "fountain", r.getPenType());
        Assert.assertEquals("默认 folderName", "默认", r.getFolderName());
        Assert.assertEquals("默认 charSpacing", 0.5f, r.getCharSpacing(), 0.001f);
        Assert.assertEquals("默认 lineSpacing", 1.6f, r.getLineSpacing(), 0.001f);
        Assert.assertEquals("默认 jitterThreshold", 0.35f, r.getJitterThreshold(), 0.001f);
        Assert.assertEquals("默认 fontPath", "fonts/NiHeWoDeLangManYuZhou-2.ttf", r.getFontPath());

        Draft d = new Draft();
        Assert.assertNotNull("Draft UUID 不为空", d.getId());
        Assert.assertEquals("Draft 默认 source", "MANUAL", d.getSource());
    }
}
