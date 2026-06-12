package com.cloudink.app.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.cloudink.app.data.local.AppDatabase;
import com.cloudink.app.data.local.entity.HandwriteRecord;

import java.util.List;

public class HistoryViewModel extends ViewModel {

    private final LiveData<List<HandwriteRecord>> allRecords;

    public HistoryViewModel() {
        allRecords = AppDatabase.getInstance()
            .handwriteRecordDao()
            .getAllRecords();
    }

    public LiveData<List<HandwriteRecord>> getAllRecords() {
        return allRecords;
    }
}
