package com.cloudink.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.cloudink.app.data.local.AppDatabase;
import com.cloudink.app.data.local.dao.AudioRecordDao;
import com.cloudink.app.data.local.dao.DraftDao;
import com.cloudink.app.data.local.dao.HandwriteRecordDao;
import com.cloudink.app.data.local.dao.TagDao;
import com.cloudink.app.data.local.entity.AudioRecord;
import com.cloudink.app.data.local.entity.Draft;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.local.entity.RecordTagCrossRef;
import com.cloudink.app.data.local.entity.Tag;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CloudInkRepository {

    private static volatile CloudInkRepository INSTANCE;

    private final HandwriteRecordDao handwriteDao;
    private final DraftDao draftDao;
    private final AudioRecordDao audioDao;
    private final TagDao tagDao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private CloudInkRepository() {
        AppDatabase db = AppDatabase.getInstance();
        handwriteDao = db.handwriteRecordDao();
        draftDao = db.draftDao();
        audioDao = db.audioRecordDao();
        tagDao = db.tagDao();
    }

    public static CloudInkRepository get(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (CloudInkRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CloudInkRepository();
                }
            }
        }
        return INSTANCE;
    }

    public void saveHandwriteRecord(HandwriteRecord record, List<String> tagNames, Runnable onDone) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            record.setUpdatedAt(now);
            if (record.getCreatedAt() <= 0) record.setCreatedAt(now);
            handwriteDao.insert(record);
            linkTags(record.getId(), tagNames);
            if (onDone != null) onDone.run();
        });
    }

    public void saveDraft(String content, String source, Runnable onDone) {
        io.execute(() -> {
            Draft d = new Draft();
            d.setId(UUID.randomUUID().toString());
            d.setContent(content);
            d.setSource(source != null ? source : "MANUAL");
            d.setCreatedAt(System.currentTimeMillis());
            draftDao.insert(d);
            if (onDone != null) onDone.run();
        });
    }

    public void saveAudioRecord(String filePath, long durationMs, String transcript, Runnable onDone) {
        io.execute(() -> {
            AudioRecord ar = new AudioRecord();
            ar.setId(UUID.randomUUID().toString());
            ar.setFilePath(filePath);
            String fn = filePath;
            int slash = filePath.lastIndexOf('/');
            if (slash >= 0) fn = filePath.substring(slash + 1);
            ar.setFileName(fn);
            ar.setDurationMs(durationMs);
            ar.setTranscript(transcript);
            ar.setCreatedAt(System.currentTimeMillis());
            audioDao.insert(ar);
            if (onDone != null) onDone.run();
        });
    }

    private void linkTags(String recordId, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return;
        for (String name : tagNames) {
            if (name == null || name.trim().isEmpty()) continue;
            Tag tag = tagDao.findByName(name.trim());
            if (tag == null) {
                tag = new Tag();
                tag.setId(UUID.randomUUID().toString());
                tag.setName(name.trim());
                tag.setColor(0xFF1A73E8);
                tagDao.insert(tag);
            }
            if (handwriteDao.countCrossRef(recordId, tag.getId()) == 0) {
                handwriteDao.insertTagCrossRef(new RecordTagCrossRef(recordId, tag.getId()));
            }
        }
    }
}
