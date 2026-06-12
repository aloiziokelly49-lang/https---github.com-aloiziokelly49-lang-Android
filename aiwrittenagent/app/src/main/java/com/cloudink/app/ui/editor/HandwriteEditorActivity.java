package com.cloudink.app.ui.editor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import com.cloudink.app.CloudInkApplication;
import com.cloudink.app.R;
import com.cloudink.app.asr.SpeechInputHelper;
import com.cloudink.app.databinding.ActivityHandwriteEditorBinding;
import com.cloudink.app.databinding.LayoutEditorAudioPanelBinding;
import com.cloudink.app.rendering.HandwritingEngine;
import com.cloudink.app.rendering.model.HandwritingParams;
import com.cloudink.app.rendering.model.PaperThemeManager;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.data.repository.CloudInkRepository;
import com.cloudink.app.ui.export.ExportPreviewActivity;

import java.util.Arrays;
import java.util.UUID;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 手写编辑器 Activity —— "所见即所得"参数调节台。 */
public class HandwriteEditorActivity extends AppCompatActivity {

    // Intent extras: 从 HistoryFragment 恢复档案
    public static final String EXTRA_RESTORE_TITLE   = "restore_title";
    public static final String EXTRA_RESTORE_CONTENT = "restore_content";
    public static final String EXTRA_RESTORE_CHAR_SP = "restore_char_sp";
    public static final String EXTRA_RESTORE_LINE_SP = "restore_line_sp";
    public static final String EXTRA_RESTORE_JITTER  = "restore_jitter";
    public static final String EXTRA_RESTORE_PAPER   = "restore_paper";
    public static final String EXTRA_RESTORE_PEN     = "restore_pen";
    public static final String EXTRA_OCR_RESULT   = "ocr_result";
    public static final String EXTRA_RESTORE_FONT = "restore_font";

    private ActivityHandwriteEditorBinding binding;
    private LayoutEditorAudioPanelBinding audioPanelBinding;
    private HandwriteEditorViewModel viewModel;

    private boolean pendingStartAfterMicGrant;

    private final ActivityResultLauncher<String> recordPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                if (pendingStartAfterMicGrant) {
                    pendingStartAfterMicGrant = false;
                    viewModel.startVoiceCapture();
                }
            } else {
                pendingStartAfterMicGrant = false;
                Toast.makeText(this, R.string.editor_voice_need_mic, Toast.LENGTH_LONG).show();
            }
        });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (pendingStartAfterMicGrant && hasRecordPermission()) {
                viewModel.startVoiceCapture();
                pendingStartAfterMicGrant = false;
            }
        });

    private final ActivityResultLauncher<Intent> systemSpeechLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            ArrayList<String> matches = result.getData().getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                viewModel.appendExternalVoiceText(matches.get(0));
                Toast.makeText(this, R.string.voice_appended, Toast.LENGTH_SHORT).show();
            }
        });

    // SeekBar 值范围
    private static final float CHAR_SPACING_MIN = 0f;
    private static final float CHAR_SPACING_MAX = 2.0f;
    private static final float LINE_SPACING_MIN = 0.8f;
    private static final float LINE_SPACING_MAX = 3.0f;
    private static final float JITTER_MIN = 0f;
    private static final float JITTER_MAX = 1.0f;
    // ================================================================
    // onCreate
    // ================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HandwriteEditorViewModel.class);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_handwrite_editor);
        binding.setViewModel(viewModel);
        binding.setVoice(viewModel.getVoiceState());
        binding.setLifecycleOwner(this);

        audioPanelBinding = binding.audioPanel;

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // 档案回溯: 如果 Intent 携带恢复参数, 则覆盖默认值
        handleRestoreIntent();

        setupSeekBars();
        setupToggleGroups();
        setupFontToggle();
        setupThemeToggle();
        setupVoicePanel();
        setupTextInput();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        viewModel.setRenderWidth(screenW);
        applyPreviewTheme();
    }

    /** 从 Intent 恢复档案参数 / 接收 OCR 或语音转写结果。 */
    private void handleRestoreIntent() {
        Intent i = getIntent();
        String content = i.getStringExtra(EXTRA_RESTORE_CONTENT);

        if (content != null) {
            // 档案回溯: 恢复所有排版参数
            HandwritingParams p = viewModel.getParams();
            p.setCharSpacing(i.getFloatExtra(EXTRA_RESTORE_CHAR_SP, 0.5f));
            p.setLineSpacing(i.getFloatExtra(EXTRA_RESTORE_LINE_SP, 1.6f));
            p.setJitterThreshold(i.getFloatExtra(EXTRA_RESTORE_JITTER, 0.35f));
            p.setPaperIndex(i.getIntExtra(EXTRA_RESTORE_PAPER, 0));
            p.setPenType(i.getStringExtra(EXTRA_RESTORE_PEN) != null
                ? i.getStringExtra(EXTRA_RESTORE_PEN) : "fountain");
            String fontPath = i.getStringExtra(EXTRA_RESTORE_FONT);
            if (fontPath != null) {
                p.setFontPath(fontPath);
                CloudInkApplication.getInstance().getHandwritingEngine().switchFont(this, fontPath);
            }
            viewModel.setInputText(content);
            binding.etInputText.setText(content);
        } else {
            // OCR 结果: 追加到输入框末尾
            String ocrText = i.getStringExtra(EXTRA_OCR_RESULT);
            if (ocrText != null && !ocrText.isEmpty()) {
                String current = viewModel.getInputText();
                if (current != null && !current.isEmpty() && !current.endsWith("\n")) {
                    current += "\n";
                }
                viewModel.setInputText((current != null ? current : "") + ocrText);
                binding.etInputText.setText(viewModel.getInputText());
                binding.etInputText.setSelection(viewModel.getInputText().length());
                Toast.makeText(this, "OCR 识别完成，文字已导入", Toast.LENGTH_SHORT).show();
            }
        }

    }

    // ================================================================
    // SeekBar 监听
    // ================================================================

    private void setupSeekBars() {
        HandwritingParams p = viewModel.getParams();

        initSeekBar(binding.sliderCharSpacing,
            toProgress(p.getCharSpacing(), CHAR_SPACING_MIN, CHAR_SPACING_MAX),
            progress -> {
                float val = toFloat(progress, CHAR_SPACING_MIN, CHAR_SPACING_MAX);
                p.setCharSpacing(val);
                binding.tvCharSpacingVal.setText(fmt(val));
            });

        initSeekBar(binding.sliderLineSpacing,
            toProgress(p.getLineSpacing(), LINE_SPACING_MIN, LINE_SPACING_MAX),
            progress -> {
                float val = toFloat(progress, LINE_SPACING_MIN, LINE_SPACING_MAX);
                p.setLineSpacing(val);
                binding.tvLineSpacingVal.setText(fmt(val));
            });

        initSeekBar(binding.sliderJitter,
            toProgress(p.getJitterThreshold(), JITTER_MIN, JITTER_MAX),
            progress -> {
                float val = toFloat(progress, JITTER_MIN, JITTER_MAX);
                p.setJitterThreshold(val);
                binding.tvJitterVal.setText(fmt(val));
            });
    }

    private void initSeekBar(SeekBar bar, int initProgress, OnProgressChanged cb) {
        bar.setProgress(initProgress);
        cb.onChanged(initProgress);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) cb.onChanged(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    // ================================================================
    // ToggleGroup 监听
    // ================================================================

    private void setupToggleGroups() {
        binding.togglePaper.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if      (checkedId == R.id.btn_paper_1) viewModel.setPaperIndex(1);
            else if (checkedId == R.id.btn_paper_2) viewModel.setPaperIndex(2);
            else if (checkedId == R.id.btn_paper_3) viewModel.setPaperIndex(3);
            else                                     viewModel.setPaperIndex(0);
        });

        binding.togglePen.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if      (checkedId == R.id.btn_pen_ballpoint) viewModel.setPenType("ballpoint");
            else if (checkedId == R.id.btn_pen_marker)    viewModel.setPenType("marker");
            else                                          viewModel.setPenType("fountain");
        });
    }

    // ================================================================
    // 字体切换 (扫描 assets/fonts/ 动态生成按钮)
    // ================================================================

    private void setupFontToggle() {
        List<String> fonts = HandwritingEngine.getAvailableFonts(this);
        if (fonts.isEmpty()) return;

        String currentFont = CloudInkApplication.getInstance()
            .getHandwritingEngine().getCurrentFontPath();

        int hPad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f,
            getResources().getDisplayMetrics());
        int minBtnW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f,
            getResources().getDisplayMetrics());

        int checkedId = View.NO_ID;
        for (String fontPath : fonts) {
            MaterialButton btn = new MaterialButton(this,
                null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(hPad / 2);
            btn.setLayoutParams(lp);
            btn.setMinWidth(minBtnW);
            btn.setPadding(hPad, btn.getPaddingTop(), hPad, btn.getPaddingBottom());
            btn.setText(HandwritingEngine.getFontDisplayName(fontPath));
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            btn.setMaxLines(2);
            btn.setAllCaps(false);
            btn.setCornerRadius((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                getResources().getDisplayMetrics()));
            btn.setTag(fontPath);
            btn.setId(View.generateViewId());
            binding.toggleFont.addView(btn);

            if (fontPath.equals(currentFont)) checkedId = btn.getId();
        }

        if (checkedId != View.NO_ID) {
            binding.toggleFont.check(checkedId);
        }

        binding.toggleFont.addOnButtonCheckedListener((group, checkedId2, isChecked) -> {
            if (!isChecked) return;
            View checked = group.findViewById(checkedId2);
            String fontPath = (String) checked.getTag();
            if (fontPath == null) return;

            CloudInkApplication.getInstance().getHandwritingEngine()
                .switchFont(HandwriteEditorActivity.this, fontPath);
            viewModel.getParams().setFontPath(fontPath);
            CloudInkApplication.getInstance().getPreferences()
                .saveDefaultFontPath(fontPath);
            // 触发重渲染
            viewModel.requestRender();
        });
    }

    // ================================================================
    // 主题切换 (护眼黄/极简白/深邃黑/简约灰)
    // ================================================================

    private void setupThemeToggle() {
        binding.toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int idx;
            if      (checkedId == R.id.btn_theme_white) idx = 1;
            else if (checkedId == R.id.btn_theme_dark)  idx = 2;
            else if (checkedId == R.id.btn_theme_gray)  idx = 3;
            else                                         idx = 0;

            PaperThemeManager.saveTheme(this, idx);
            PaperThemeManager theme = PaperThemeManager.fromIndex(idx);
            CloudInkApplication.getInstance().getHandwritingEngine().setTheme(theme);
            applyPreviewTheme();
            viewModel.requestRender();
        });
    }

    /** 预览区外框与画布底色与当前纸张主题一致。 */
    private void applyPreviewTheme() {
        int idx = PaperThemeManager.loadThemeIndex(this);
        PaperThemeManager theme = PaperThemeManager.fromIndex(idx);
        int bg = theme.getCanvasColor();
        binding.previewFrame.setBackgroundColor(bg);
        binding.previewCanvas.refreshTheme(this);
    }

    // ================================================================
    // 文本输入
    // ================================================================

    private void setupVoicePanel() {
        audioPanelBinding.fabVoice.setOnClickListener(v -> {
            viewModel.setVoicePanelExpanded(true);
            requestVoiceCaptureWithPermissions();
        });

        audioPanelBinding.btnCollapsePanel.setOnClickListener(v -> {
            if (viewModel.isVoiceCapturing()) {
                viewModel.setVoicePanelExpanded(false);
            } else {
                viewModel.setVoicePanelExpanded(false);
            }
        });

        audioPanelBinding.btnVoiceIme.setOnClickListener(v -> openKeyboardForVoiceInput());

        audioPanelBinding.btnVoiceStart.setOnClickListener(v -> requestVoiceCaptureWithPermissions());

        audioPanelBinding.btnVoiceAppend.setOnClickListener(v -> {
            viewModel.appendVoiceToDraft();
            Toast.makeText(this, R.string.editor_voice_appended, Toast.LENGTH_SHORT).show();
        });

        viewModel.getVoiceState().addOnPropertyChangedCallback(
            new androidx.databinding.Observable.OnPropertyChangedCallback() {
                @Override
                public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId) {
                    if (propertyId == com.cloudink.app.BR.liveTranscript
                        || propertyId == com.cloudink.app.BR.transcribing) {
                        viewModel.getVoiceState().refreshAppendEnabled();
                    }
                }
            });
    }

    private void requestVoiceCaptureWithPermissions() {
        if (!hasRecordPermission()) {
            pendingStartAfterMicGrant = true;
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingStartAfterMicGrant = true;
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        viewModel.startVoiceCapture();
    }

    private boolean hasRecordPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void openKeyboardForVoiceInput() {
        viewModel.setVoicePanelExpanded(true);
        binding.etInputText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etInputText, InputMethodManager.SHOW_IMPLICIT);
        }
        Toast.makeText(this, R.string.voice_keyboard_ime_toast, Toast.LENGTH_LONG).show();
    }

    private void setupTextInput() {
        binding.etInputText.setVerticalScrollBarEnabled(true);
        binding.etInputText.setMovementMethod(new ScrollingMovementMethod());
        
        // 解决外层ScrollView与EditText滚动冲突
        binding.etInputText.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        
        viewModel.inputText.addOnPropertyChangedCallback(
            new androidx.databinding.Observable.OnPropertyChangedCallback() {
                @Override
                public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId) {
                    viewModel.requestRender();
                }
            });
    }

    // ================================================================
    // 工具
    // ================================================================

    private static int toProgress(float value, float min, float max) {
        return Math.round((value - min) / (max - min) * 100f);
    }

    private static float toFloat(int progress, float min, float max) {
        return min + (progress / 100f) * (max - min);
    }

    private static String fmt(float v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private interface OnProgressChanged {
        void onChanged(int progress);
    }

    // ================================================================
    // Toolbar 菜单: 预览/导出按钮
    // ================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            onSaveRecord();
            return true;
        }
        if (item.getItemId() == R.id.action_export) {
            onExportClicked();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onSaveRecord() {
        HandwritingParams p = viewModel.getParams();
        HandwriteRecord record = new HandwriteRecord();
        record.setId(UUID.randomUUID().toString());
        String text = viewModel.getInputText();
        String title = text.length() > 12 ? text.substring(0, 12) + "…" : text;
        if (title.isEmpty()) title = "未命名草稿";
        record.setTitle(title);
        record.setContent(text);
        record.setCharSpacing(p.getCharSpacing());
        record.setLineSpacing(p.getLineSpacing());
        record.setJitterThreshold(p.getJitterThreshold());
        record.setPaperIndex(p.getPaperIndex());
        record.setPenType(p.getPenType());
        record.setFontPath(p.getFontPath() != null ? p.getFontPath() : "fonts/NiHeWoDeLangManYuZhou-2.ttf");
        record.setFolderName("默认");

        CloudInkRepository.get(this).saveHandwriteRecord(record,
            Arrays.asList("手写", "草稿"),
            () -> runOnUiThread(() ->
                Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show()));
    }

    /** 将当前排版参数 + 内容打包发送到 ExportPreviewActivity。 */
    private void onExportClicked() {
        HandwritingParams p = viewModel.getParams();
        Intent intent = new Intent(this, ExportPreviewActivity.class);
        intent.putExtra(ExportPreviewActivity.EXTRA_TEXT,    viewModel.getInputText());
        intent.putExtra(ExportPreviewActivity.EXTRA_CHAR_SP, p.getCharSpacing());
        intent.putExtra(ExportPreviewActivity.EXTRA_LINE_SP, p.getLineSpacing());
        intent.putExtra(ExportPreviewActivity.EXTRA_JITTER,  p.getJitterThreshold());
        intent.putExtra(ExportPreviewActivity.EXTRA_PAPER,   p.getPaperIndex());
        intent.putExtra(ExportPreviewActivity.EXTRA_PEN,     p.getPenType());
        intent.putExtra(ExportPreviewActivity.EXTRA_FONT,    p.getFontPath());
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (viewModel.isVoiceCapturing()) {
            viewModel.interruptVoiceCapture();
            Toast.makeText(this, R.string.editor_voice_interrupt, Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null && viewModel.isVoiceCapturing()) {
            viewModel.stopVoiceCapture();
        }
        super.onDestroy();
        binding = null;
        audioPanelBinding = null;
    }
}
