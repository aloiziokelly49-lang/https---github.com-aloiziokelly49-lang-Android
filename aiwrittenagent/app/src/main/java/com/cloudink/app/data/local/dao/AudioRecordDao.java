package com.cloudink.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cloudink.app.data.local.entity.AudioRecord;

import java.util.List;

@Dao
public interface AudioRecordDao {

    @Query("SELECT * FROM audio_records ORDER BY created_at DESC")
    LiveData<List<AudioRecord>> getAllRecords();

    @Query("SELECT * FROM audio_records WHERE record_id = :recordId")
    LiveData<List<AudioRecord>> getRecordsByHandwriteId(String recordId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AudioRecord record);

    @Delete
    void delete(AudioRecord record);

    @Query("DELETE FROM audio_records WHERE id = :id")
    void deleteById(String id);
}
