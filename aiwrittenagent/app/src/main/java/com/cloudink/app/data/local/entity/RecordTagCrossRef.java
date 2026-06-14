package com.cloudink.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
    tableName = "record_tag_join",

    //多重标签，使用复合主键（record_id + tag_id）来唯一标识每条记录-标签关联，
    primaryKeys = {"record_id", "tag_id"},
    foreignKeys = {
        @ForeignKey(
            entity = HandwriteRecord.class,
            parentColumns = "id",
            childColumns = "record_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = Tag.class,
            parentColumns = "id",
            childColumns = "tag_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {@Index("tag_id")}
)
public class RecordTagCrossRef {

    @ColumnInfo(name = "record_id")
    @NonNull
    private String recordId = "";

    @ColumnInfo(name = "tag_id")
    @NonNull
    private String tagId = "";

    public RecordTagCrossRef() {}

    @androidx.room.Ignore
    public RecordTagCrossRef(@NonNull String recordId, @NonNull String tagId) {
        this.recordId = recordId;
        this.tagId = tagId;
    }

    @NonNull
    public String getRecordId() { return recordId; }
    public void setRecordId(@NonNull String recordId) { this.recordId = recordId; }

    @NonNull
    public String getTagId() { return tagId; }
    public void setTagId(@NonNull String tagId) { this.tagId = tagId; }
}
