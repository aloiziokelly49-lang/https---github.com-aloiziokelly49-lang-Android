package com.cloudink.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

// 草稿箱实体类，表示用户的草稿数据
@Entity(tableName = "drafts")
public class Draft {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id = UUID.randomUUID().toString();
    // 使用 UUID 生成唯一 ID，作为主键，
    // 确保每条草稿记录的唯一性

    @ColumnInfo(name = "content")
    private String content;

    /** Source: OCR, ASR, MANUAL, EXTRACT */
    @ColumnInfo(name = "source")
    @NonNull
    private String source = "MANUAL";

    @ColumnInfo(name = "created_at")
    private long createdAt = System.currentTimeMillis();

    public Draft() {}

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    @NonNull
    public String getSource() { return source; }
    public void setSource(@NonNull String source) { this.source = source; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
