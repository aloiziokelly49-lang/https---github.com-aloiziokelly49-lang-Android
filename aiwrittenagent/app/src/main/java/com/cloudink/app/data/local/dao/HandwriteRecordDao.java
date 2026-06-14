package com.cloudink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.RecordTagCrossRef;
import com.cloudink.app.data.local.entity.Tag;

import java.util.List;

// 手写记录DAO接口，管理用户的手写记录数据
@Dao
public interface HandwriteRecordDao {

    @Query("SELECT * FROM handwrite_records ORDER BY updated_at DESC")
    LiveData<List<HandwriteRecord>> getAllRecords();

    // 根据文件夹名称查询手写记录，按更新时间降序排序
    @Query("SELECT * FROM handwrite_records WHERE folder_name = :folder ORDER BY updated_at DESC")
    LiveData<List<HandwriteRecord>> getRecordsByFolder(String folder);

    // 根据标签ID（标签类别）查询手写记录，按更新时间降序排序
    @Query("SELECT DISTINCT h.* FROM handwrite_records h "
         + "INNER JOIN record_tag_join j ON h.id = j.record_id "
         + "WHERE j.tag_id = :tagId ORDER BY h.updated_at DESC")
    LiveData<List<HandwriteRecord>> getRecordsByTag(String tagId);

    // 根据ID查询手写记录
    @Query("SELECT * FROM handwrite_records WHERE id = :id")
    LiveData<HandwriteRecord> getRecordById(String id);

    @Query("SELECT * FROM handwrite_records WHERE id = :id")
    HandwriteRecord getRecordByIdSync(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HandwriteRecord record);

    @Update
    void update(HandwriteRecord record);

    @Delete
    void delete(HandwriteRecord record);

    @Query("DELETE FROM handwrite_records WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT DISTINCT folder_name FROM handwrite_records ORDER BY folder_name")
    LiveData<List<String>> getAllFolders();

    // === 多对多关联: Record ↔ Tag ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTagCrossRef(RecordTagCrossRef crossRef);

    //根据多重标签查询手写记录，按更新时间降序排序
    @Query("SELECT t.* FROM tags t "
         + "INNER JOIN record_tag_join j ON t.id = j.tag_id "
         + "WHERE j.record_id = :recordId ORDER BY t.name")
    LiveData<List<Tag>> getTagsForRecord(String recordId);

    @Query("SELECT t.* FROM tags t "
         + "INNER JOIN record_tag_join j ON t.id = j.tag_id "
         + "WHERE j.record_id = :recordId ORDER BY t.name")
    List<Tag> getTagsForRecordSync(String recordId);

    @Query("SELECT COUNT(*) FROM record_tag_join WHERE record_id = :recordId AND tag_id = :tagId")
    int countCrossRef(String recordId, String tagId);

    @Query("SELECT COUNT(*) FROM handwrite_records")
    int getCountSync();
}
