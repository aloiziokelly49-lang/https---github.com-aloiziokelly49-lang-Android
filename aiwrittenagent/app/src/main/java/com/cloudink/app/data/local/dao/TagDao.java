package com.cloudink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cloudink.app.data.local.entity.Tag;

import java.util.List;


// 标签DAO接口，管理用户的标签数据
@Dao
public interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    LiveData<List<Tag>> getAllTags();

    @Query("SELECT * FROM tags WHERE id = :id")
    Tag getTagByIdSync(String id);

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    Tag findByName(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Tag tag);

    @Delete
    void delete(Tag tag);

    @Query("DELETE FROM tags WHERE id = :id")
    void deleteById(String id);
}
