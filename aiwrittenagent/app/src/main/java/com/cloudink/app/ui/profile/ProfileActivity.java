package com.cloudink.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cloudink.app.CloudInkApplication;
import com.cloudink.app.R;
import com.cloudink.app.data.preferences.AppPreferences;
import com.cloudink.app.ui.home.HomeActivity;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileActivity extends AppCompatActivity {

    public static final String EXTRA_FIRST_TIME = "extra_first_time";

    private static final float CHAR_MIN = 0f, CHAR_MAX = 2f;
    private static final float LINE_MIN = 0.8f, LINE_MAX = 3f;
    private static final float JITTER_MIN = 0f, JITTER_MAX = 1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        AppPreferences prefs = CloudInkApplication.getInstance().getPreferences();

        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());

        SeekBar sliderChar = findViewById(R.id.slider_char);
        SeekBar sliderLine = findViewById(R.id.slider_line);
        SeekBar sliderJitter = findViewById(R.id.slider_jitter);
        TextInputEditText etNickname = findViewById(R.id.et_nickname);

        sliderChar.setProgress(toProgress(prefs.getDefaultCharSpacing(), CHAR_MIN, CHAR_MAX));
        sliderLine.setProgress(toProgress(prefs.getDefaultLineSpacing(), LINE_MIN, LINE_MAX));
        sliderJitter.setProgress(toProgress(prefs.getDefaultJitter(), JITTER_MIN, JITTER_MAX));

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            prefs.saveDefaultCharSpacing(toValue(sliderChar.getProgress(), CHAR_MIN, CHAR_MAX));
            prefs.saveDefaultLineSpacing(toValue(sliderLine.getProgress(), LINE_MIN, LINE_MAX));
            prefs.saveDefaultJitter(toValue(sliderJitter.getProgress(), JITTER_MIN, JITTER_MAX));
            if (etNickname.getText() != null && !etNickname.getText().toString().trim().isEmpty()) {
                prefs.setDisplayName(etNickname.getText().toString().trim());
            }
            prefs.setFirstLoginDone();
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
            if (getIntent().getBooleanExtra(EXTRA_FIRST_TIME, false)) {
                startActivity(new Intent(this, HomeActivity.class));
            }
            finish();
        });
    }

    private static int toProgress(float value, float min, float max) {
        return Math.round((value - min) / (max - min) * 100f);
    }

    private static float toValue(int progress, float min, float max) {
        return min + (progress / 100f) * (max - min);
    }
}
