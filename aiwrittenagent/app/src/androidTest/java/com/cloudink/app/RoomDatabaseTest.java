package com.cloudink.app;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.cloudink.app.data.local.AppDatabase;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.RecordTagCrossRef;
import com.cloudink.app.data.local.entity.Tag;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RoomDatabaseTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
    }

    @After
    public void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    /**
     * 冒烟测试: 插入一条 HandwriteRecord + 一个 Tag + 多对多关联，
     * 然后通过 DAO 查询验证数据完整性。
     */
    @Test
    public void testHandwriteRecordAndTagInsertion() throws InterruptedException {
        // 1. 插入 HandwriteRecord
        HandwriteRecord record = new HandwriteRecord();
        record.setTitle("单元测试笔记");
        record.setContent("自动化测试内容——验证 Room 数据库读写一致性。");
        record.setPaperIndex(1);
        record.setPenType("ballpoint");
        record.setFolderName("测试文件夹");
        db.handwriteRecordDao().insert(record);

        // 2. 插入 Tag
        Tag tag = new Tag();
        tag.setName("紧急");
        tag.setColor(0xFFFF5722);
        db.tagDao().insert(tag);

        // 3. 建立多对多关联
        RecordTagCrossRef crossRef = new RecordTagCrossRef(record.getId(), tag.getId());
        db.handwriteRecordDao().insertTagCrossRef(crossRef);

        // 4. 同步查询 HandwriteRecord
        HandwriteRecord queried = db.handwriteRecordDao().getRecordByIdSync(record.getId());
        Assert.assertNotNull("查询结果不应为空", queried);
        Assert.assertEquals("标题应一致", record.getTitle(), queried.getTitle());
        Assert.assertEquals("内容应一致", record.getContent(), queried.getContent());
        Assert.assertEquals("纸张索引应一致", record.getPaperIndex(), queried.getPaperIndex());
        Assert.assertEquals("笔触应一致", record.getPenType(), queried.getPenType());
        Assert.assertEquals("文件夹应一致", record.getFolderName(), queried.getFolderName());
        Assert.assertEquals("UUID 应一致", record.getId(), queried.getId());

        // 5. 验证交叉引用
        int count = db.handwriteRecordDao().countCrossRef(record.getId(), tag.getId());
        Assert.assertEquals("交叉引用应存在", 1, count);

        // 6. 反向查询 — 通过 recordId 查出关联的 tags
        List<Tag> tags = getValue(db.handwriteRecordDao().getTagsForRecord(record.getId()));
        Assert.assertNotNull("标签列表不应为空", tags);
        Assert.assertEquals("应查询到 1 个标签", 1, tags.size());
        Assert.assertEquals("标签名称应一致", tag.getName(), tags.get(0).getName());
        Assert.assertEquals("标签颜色应一致", tag.getColor(), tags.get(0).getColor());

        // 7. 通过 tagId 反查 records
        List<HandwriteRecord> recordsByTag = getValue(
            db.handwriteRecordDao().getRecordsByTag(tag.getId()));
        Assert.assertNotNull("按标签查询结果不应为空", recordsByTag);
        Assert.assertEquals("按标签应查到 1 条记录", 1, recordsByTag.size());
        Assert.assertEquals("按标签查到的记录ID应一致", record.getId(), recordsByTag.get(0).getId());
    }

    /** 等待 LiveData 回传一次并返回数据，超时 3 秒。 */
    private static <T> T getValue(LiveData<T> liveData) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final Object[] result = new Object[1];
        liveData.observeForever(new Observer<T>() {
            @Override
            public void onChanged(T t) {
                result[0] = t;
                latch.countDown();
            }
        });
        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw new RuntimeException("LiveData 超时未返回数据");
        }
        return (T) result[0];
    }
}
