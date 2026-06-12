package com.cloudink.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "tags")
public class Tag {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id = UUID.randomUUID().toString();

    @ColumnInfo(name = "name")
    @NonNull
    private String name = "";

    @ColumnInfo(name = "color")
    private int color;

    public Tag() {}

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
}
