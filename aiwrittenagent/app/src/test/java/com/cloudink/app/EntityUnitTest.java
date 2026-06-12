package com.cloudink.app;

import com.cloudink.app.data.local.entity.AudioRecord;
import com.cloudink.app.data.local.entity.Draft;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.RecordTagCrossRef;
import com.cloudink.app.data.local.entity.Tag;
import com.cloudink.app.util.UuidGenerator;

import org.junit.Assert;
import org.junit.Test;

/**
 * 纯 JVM 单元测试 — 验证 Entity POJO 的 getter/setter、UUID 自动生成、默认值。
 * 不依赖 Android 框架，可在 testDebugUnitTest 中直接运行。
 */
public class EntityUnitTest {

    // ======= HandwriteRecord =======
    @Test
    public void testHandwriteRecord_defaultsAndSetters() {
        HandwriteRecord r = new HandwriteRecord();

        // UUID 自动生成
        Assert.assertNotNull("UUID 不应为空", r.getId());
        Assert.assertFalse("UUID 不应为空字符串", r.getId().isEmpty());
        Assert.assertEquals("UUID 应符合标准长度", 36, r.getId().length());

        // 默认值
        Assert.assertEquals("默认标题", "", r.getTitle());
        Assert.assertEquals("默认笔触", "fountain", r.getPenType());
        Assert.assertEquals("默认文件夹", "默认", r.getFolderName());

        // Setter → Getter 一致性
        r.setTitle("新标题");
        r.setContent("测试内容");
        r.setPaperIndex(3);
        r.setPenType("pencil");
        r.setFolderName("工作");

        Assert.assertEquals("标题 setter/getter 应一致", "新标题", r.getTitle());
        Assert.assertEquals("内容 setter/getter 应一致", "测试内容", r.getContent());
        Assert.assertEquals("纸张索引 setter/getter 应一致", 3, r.getPaperIndex());
        Assert.assertEquals("笔触 setter/getter 应一致", "pencil", r.getPenType());
        Assert.assertEquals("文件夹 setter/getter 应一致", "工作", r.getFolderName());
    }

    // ======= Draft =======
    @Test
    public void testDraft_defaultsAndSetters() {
        Draft d = new Draft();
        Assert.assertNotNull("UUID 不应为空", d.getId());
        Assert.assertEquals("默认来源", "MANUAL", d.getSource());

        d.setContent("草稿内容");
        d.setSource("OCR");
        Assert.assertEquals("内容应一致", "草稿内容", d.getContent());
        Assert.assertEquals("来源应一致", "OCR", d.getSource());
    }

    // ======= AudioRecord =======
    @Test
    public void testAudioRecord_defaultsAndSetters() {
        AudioRecord a = new AudioRecord();
        Assert.assertNotNull("UUID 不应为空", a.getId());
        Assert.assertEquals("默认文件名", "", a.getFileName());
        Assert.assertEquals("默认路径", "", a.getFilePath());

        a.setFileName("recording_001.m4a");
        a.setFilePath("/data/audio/recording_001.m4a");
        a.setDurationMs(15000L);
        a.setTranscript("转写文本");

        Assert.assertEquals("文件名应一致", "recording_001.m4a", a.getFileName());
        Assert.assertEquals("路径应一致", "/data/audio/recording_001.m4a", a.getFilePath());
        Assert.assertEquals("时长应一致", 15000L, a.getDurationMs());
        Assert.assertEquals("转写应一致", "转写文本", a.getTranscript());
    }

    // ======= Tag =======
    @Test
    public void testTag_defaultsAndSetters() {
        Tag t = new Tag();
        Assert.assertNotNull("UUID 不应为空", t.getId());

        t.setName("重要");
        t.setColor(0xFFFF0000);
        Assert.assertEquals("标签名应一致", "重要", t.getName());
        Assert.assertEquals("颜色应一致", 0xFFFF0000, t.getColor());
    }

    // ======= RecordTagCrossRef =======
    @Test
    public void testRecordTagCrossRef_constructors() {
        // 无参构造 + setter
        RecordTagCrossRef c1 = new RecordTagCrossRef();
        c1.setRecordId("record-001");
        c1.setTagId("tag-001");
        Assert.assertEquals("recordId setter 应一致", "record-001", c1.getRecordId());
        Assert.assertEquals("tagId setter 应一致", "tag-001", c1.getTagId());

        // 有参构造
        RecordTagCrossRef c2 = new RecordTagCrossRef("record-002", "tag-002");
        Assert.assertEquals("有参构造 recordId", "record-002", c2.getRecordId());
        Assert.assertEquals("有参构造 tagId", "tag-002", c2.getTagId());
    }

    // ======= UuidGenerator =======
    @Test
    public void testUuidGenerator_uniqueness() {
        String uuid1 = UuidGenerator.generate();
        String uuid2 = UuidGenerator.generate();
        Assert.assertNotNull("UUID1 不应为空", uuid1);
        Assert.assertNotNull("UUID2 不应为空", uuid2);
        Assert.assertEquals("UUID 长度为 36", 36, uuid1.length());
        Assert.assertNotEquals("两个 UUID 应不同", uuid1, uuid2);
    }
}
