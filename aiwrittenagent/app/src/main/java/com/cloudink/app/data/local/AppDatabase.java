package com.cloudink.app.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.cloudink.app.data.local.converter.DateConverter;
import com.cloudink.app.data.local.dao.AudioRecordDao;
import com.cloudink.app.data.local.dao.DraftDao;
import com.cloudink.app.data.local.dao.HandwriteRecordDao;
import com.cloudink.app.data.local.dao.TagDao;
import com.cloudink.app.data.local.entity.AudioRecord;
import com.cloudink.app.data.local.entity.Draft;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.RecordTagCrossRef;
import com.cloudink.app.data.local.entity.Tag;

import java.util.concurrent.Executors;

@Database(
    entities = {
        HandwriteRecord.class, Draft.class, AudioRecord.class,
        Tag.class, RecordTagCrossRef.class
    },
    version = 4,
    exportSchema = true
)
@TypeConverters(DateConverter.class)
public abstract class AppDatabase extends RoomDatabase {

    public abstract HandwriteRecordDao handwriteRecordDao();
    public abstract DraftDao draftDao();
    public abstract AudioRecordDao audioRecordDao();
    public abstract TagDao tagDao();

    private static volatile AppDatabase INSTANCE;

    /** v1 → v2: 为 handwrite_records 添加排版参数字段 (char_spacing, line_spacing, jitter_threshold) */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE handwrite_records ADD COLUMN char_spacing REAL NOT NULL DEFAULT 0.5");
            db.execSQL("ALTER TABLE handwrite_records ADD COLUMN line_spacing REAL NOT NULL DEFAULT 1.6");
            db.execSQL("ALTER TABLE handwrite_records ADD COLUMN jitter_threshold REAL NOT NULL DEFAULT 0.35");
        }
    };

    /** v2 → v3: 为 handwrite_records 添加字体路径字段 */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE handwrite_records ADD COLUMN font_path TEXT NOT NULL DEFAULT 'fonts/NiHeWoDeLangManYuZhou-2.ttf'");
        }
    };

    /** v3 → v4: 为 handwrite_records 添加存储路径字段 */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE handwrite_records ADD COLUMN storage_path TEXT NOT NULL DEFAULT '内部存储/历史档案室'");
        }
    };

    public static AppDatabase create(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "cloudink.db"
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                Tag defaultTag = new Tag();
                                defaultTag.setName("未分类");
                                defaultTag.setColor(0xFF5F6368);
                                INSTANCE.tagDao().insert(defaultTag);
                            });
                        }
                    })
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    public static AppDatabase createInMemory(@NonNull Context context) {
        return Room.inMemoryDatabaseBuilder(
            context.getApplicationContext(),
            AppDatabase.class
        ).build();
    }

    public static AppDatabase getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("AppDatabase not initialized. Call create(context) first.");
        }
        return INSTANCE;
    }
}
