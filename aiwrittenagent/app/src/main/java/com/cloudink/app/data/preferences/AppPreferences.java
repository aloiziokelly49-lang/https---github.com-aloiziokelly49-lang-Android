package com.cloudink.app.data.preferences;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AppPreferences {

    private static final String PREF_NAME = "cloudink_settings";

    public static final Preferences.Key<Boolean> KEY_IS_LOGGED_IN =
        PreferencesKeys.booleanKey("is_logged_in");
    public static final Preferences.Key<String> KEY_USER_PHONE =
        PreferencesKeys.stringKey("user_phone");
    public static final Preferences.Key<Boolean> KEY_IS_FIRST_LOGIN =
        PreferencesKeys.booleanKey("is_first_login");
    public static final Preferences.Key<Float> KEY_DEFAULT_CHAR_SPACING =
        PreferencesKeys.floatKey("default_char_spacing");
    public static final Preferences.Key<Float> KEY_DEFAULT_LINE_SPACING =
        PreferencesKeys.floatKey("default_line_spacing");
    public static final Preferences.Key<Float> KEY_DEFAULT_JITTER =
        PreferencesKeys.floatKey("default_jitter");
    public static final Preferences.Key<Integer> KEY_DEFAULT_PAPER_INDEX =
        PreferencesKeys.intKey("default_paper_index");
    public static final Preferences.Key<String> KEY_DEFAULT_PEN_TYPE =
        PreferencesKeys.stringKey("default_pen_type");
    public static final Preferences.Key<String> KEY_DEFAULT_FONT_PATH =
        PreferencesKeys.stringKey("default_font_path");
    public static final Preferences.Key<String> KEY_DISPLAY_NAME =
        PreferencesKeys.stringKey("display_name");

    private final RxDataStore<Preferences> dataStore;

    public AppPreferences(@NonNull Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(context, PREF_NAME).build();
    }

    // --- Login state ---
    public Single<Boolean> isLoggedIn() {
        return dataStore.data().firstOrError()
            .map(prefs -> {
                Boolean val = prefs.get(KEY_IS_LOGGED_IN);
                return val != null ? val : false;
            });
    }

    public void setLoggedIn(boolean loggedIn, String phone) {
        dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutable = prefs.toMutablePreferences();
            mutable.set(KEY_IS_LOGGED_IN, loggedIn);
            mutable.set(KEY_USER_PHONE, phone);
            return Single.just(mutable);
        }).subscribeOn(Schedulers.io()).subscribe();
    }

    // --- First login ---
    public Single<Boolean> isFirstLogin() {
        return dataStore.data().firstOrError()
            .map(prefs -> {
                Boolean val = prefs.get(KEY_IS_FIRST_LOGIN);
                return val != null ? val : true;
            });
    }

    public void setFirstLoginDone() {
        dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutable = prefs.toMutablePreferences();
            mutable.set(KEY_IS_FIRST_LOGIN, false);
            return Single.just(mutable);
        }).subscribeOn(Schedulers.io()).subscribe();
    }

    // --- Handwriting preferences (blocking reads for settings page) ---
    public float getDefaultCharSpacing() {
        return getBlocking(KEY_DEFAULT_CHAR_SPACING, 0.5f);
    }

    public float getDefaultLineSpacing() {
        return getBlocking(KEY_DEFAULT_LINE_SPACING, 1.5f);
    }

    public float getDefaultJitter() {
        return getBlocking(KEY_DEFAULT_JITTER, 0.3f);
    }

    public int getDefaultPaperIndex() {
        return getBlocking(KEY_DEFAULT_PAPER_INDEX, 0);
    }

    public String getDefaultPenType() {
        return getBlocking(KEY_DEFAULT_PEN_TYPE, "fountain");
    }

    public void saveDefaultCharSpacing(float value) {
        put(KEY_DEFAULT_CHAR_SPACING, value);
    }

    public void saveDefaultLineSpacing(float value) {
        put(KEY_DEFAULT_LINE_SPACING, value);
    }

    public void saveDefaultJitter(float value) {
        put(KEY_DEFAULT_JITTER, value);
    }

    public void saveDefaultPaperIndex(int value) {
        put(KEY_DEFAULT_PAPER_INDEX, value);
    }

    public void saveDefaultPenType(String value) {
        put(KEY_DEFAULT_PEN_TYPE, value);
    }

    public String getDefaultFontPath() {
        return getBlocking(KEY_DEFAULT_FONT_PATH, "fonts/NiHeWoDeLangManYuZhou-2.ttf");
    }

    public void saveDefaultFontPath(String value) {
        put(KEY_DEFAULT_FONT_PATH, value);
    }

    public void setDisplayName(String name) {
        put(KEY_DISPLAY_NAME, name);
    }

    // --- Generic helpers ---
    @SuppressWarnings("unchecked")
    private <T> T getBlocking(Preferences.Key<T> key, T defaultValue) {
        try {
            return (T) dataStore.data().firstOrError()
                .map(prefs -> {
                    T val = prefs.get(key);
                    return val != null ? val : defaultValue;
                })
                .subscribeOn(Schedulers.io()).blockingGet();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private <T> void put(Preferences.Key<T> key, T value) {
        dataStore.updateDataAsync(prefs -> {
            MutablePreferences mutable = prefs.toMutablePreferences();
            mutable.set(key, value);
            return Single.just(mutable);
        }).subscribeOn(Schedulers.io()).subscribe();
    }
}
