package com.cloudink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cloudink.app.data.local.entity.Draft;

import java.util.List;

@Dao
public interface DraftDao {

    @Query("SELECT * FROM drafts ORDER BY created_at DESC")
    LiveData<List<Draft>> getAllDrafts();

    @Query("SELECT * FROM drafts WHERE source = :source ORDER BY created_at DESC")
    LiveData<List<Draft>> getDraftsBySource(String source);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Draft draft);

    @Delete
    void delete(Draft draft);

    @Query("DELETE FROM drafts")
    void deleteAll();
}
