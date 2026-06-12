package com.cloudink.app;

import android.app.Application;

import com.cloudink.app.data.local.AppDatabase;
import com.cloudink.app.data.preferences.AppPreferences;
import com.cloudink.app.ocr.TesseractOcrManager;

/** Application entry point. Initializes Room and DataStore singletons. */
public class CloudInkApplication extends Application {

    private static CloudInkApplication instance;
    private AppDatabase database;
    private AppPreferences preferences;
    private com.cloudink.app.rendering.HandwritingEngine handwritingEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.create(this);
        preferences = new AppPreferences(this);
        // 全局初始化手写字体
        handwritingEngine = new com.cloudink.app.rendering.HandwritingEngine();
        handwritingEngine.initFont(this);

        // 后台从 assets 复制 OCR 中文语言包（离线，无需联网）
        TesseractOcrManager.prepareAsync(this);
    }

    public com.cloudink.app.rendering.HandwritingEngine getHandwritingEngine() {
        return handwritingEngine;
    }

    public static CloudInkApplication getInstance() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public AppPreferences getPreferences() {
        return preferences;
    }
}
