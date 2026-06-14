package com.cloudink.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "handwrite_records")
public class HandwriteRecord {

    // 手写记录实体类，表示用户的手写记录数据

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id = UUID.randomUUID().toString();
    // 使用UUID生成唯一ID，作为主键，
    // 确保每条手写记录的唯一性

    @ColumnInfo(name = "title")
    @NonNull
    private String title = "";

    @ColumnInfo(name = "content")
    private String content;

    @ColumnInfo(name = "image_path")
    private String imagePath;

    @ColumnInfo(name = "paper_index")
    private int paperIndex;

    @ColumnInfo(name = "pen_type")
    @NonNull
    private String penType = "fountain";

    @ColumnInfo(name = "created_at")
    private long createdAt = System.currentTimeMillis();

    @ColumnInfo(name = "updated_at")
    private long updatedAt = System.currentTimeMillis();

    @ColumnInfo(name = "folder_name")
    @NonNull
    private String folderName = "默认";

    // Phase 3: 排版参数持久化 (支持档案回溯编辑)
    @ColumnInfo(name = "char_spacing")
    private float charSpacing = 0.5f;

    @ColumnInfo(name = "line_spacing")
    private float lineSpacing = 1.6f;

    @ColumnInfo(name = "jitter_threshold")
    private float jitterThreshold = 0.35f;

   
    @ColumnInfo(name = "font_path")
    @NonNull
    private String fontPath = "fonts/NiHeWoDeLangManYuZhou-2.ttf";

    // 存储路径字段，记录手写记录的实际存储位置（内部存储/外部存储/云端）
    @ColumnInfo(name = "storage_path")
    @NonNull
    private String storagePath = "内部存储/历史档案室";

    public HandwriteRecord() {}

    @NonNull public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    @NonNull public String getTitle() { return title; }
    public void setTitle(@NonNull String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public int getPaperIndex() { return paperIndex; }
    public void setPaperIndex(int paperIndex) { this.paperIndex = paperIndex; }
    @NonNull public String getPenType() { return penType; }
    public void setPenType(@NonNull String penType) { this.penType = penType; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    @NonNull public String getFolderName() { return folderName; }
    public void setFolderName(@NonNull String folderName) { this.folderName = folderName; }
    public float getCharSpacing() { return charSpacing; }
    public void setCharSpacing(float v) { this.charSpacing = v; }
    public float getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(float v) { this.lineSpacing = v; }
    public float getJitterThreshold() { return jitterThreshold; }
    public void setJitterThreshold(float v) { this.jitterThreshold = v; }
    @NonNull public String getFontPath() { return fontPath; }
    public void setFontPath(@NonNull String v) { this.fontPath = v; }
    @NonNull public String getStoragePath() { return storagePath; }
    public void setStoragePath(@NonNull String storagePath) { this.storagePath = storagePath; }
}
