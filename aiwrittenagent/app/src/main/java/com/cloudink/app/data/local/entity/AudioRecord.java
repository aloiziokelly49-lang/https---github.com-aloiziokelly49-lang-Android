package com.cloudink.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(
    tableName = "audio_records",
    foreignKeys = @ForeignKey(
        entity = HandwriteRecord.class,
        parentColumns = "id",
        childColumns = "record_id",
        onDelete = ForeignKey.SET_NULL
    ),
    indices = {@Index("record_id")}
)
public class AudioRecord {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id = UUID.randomUUID().toString();

    @ColumnInfo(name = "file_name")
    @NonNull
    private String fileName = "";

    @ColumnInfo(name = "file_path")
    @NonNull
    private String filePath = "";

    @ColumnInfo(name = "duration_ms")
    private long durationMs;

    @ColumnInfo(name = "transcript")
    private String transcript;

    @ColumnInfo(name = "record_id")
    private String recordId;

    @ColumnInfo(name = "created_at")
    private long createdAt = System.currentTimeMillis();

    public AudioRecord() {}

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    @NonNull
    public String getFileName() { return fileName; }
    public void setFileName(@NonNull String fileName) { this.fileName = fileName; }

    @NonNull
    public String getFilePath() { return filePath; }
    public void setFilePath(@NonNull String filePath) { this.filePath = filePath; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
