package com.cloudink.app.ui.asr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cloudink.app.R;
import com.cloudink.app.asr.BaiduApiErrors;
import com.cloudink.app.asr.BaiduAsrConfig;
import com.cloudink.app.asr.BaiduAsrHelper;
import com.cloudink.app.asr.BaiduRealtimeAsrHelper;
import com.cloudink.app.asr.SpeechInputHelper;
import com.cloudink.app.databinding.ActivityVoiceTranscribeBinding;
import com.cloudink.app.ui.editor.HandwriteEditorActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * 语音转写库：微信式按住说话，松手写入编辑区；支持系统语音输入与导入录音文件。
 */
public class VoiceTranscribeActivity extends AppCompatActivity {

    public static final String EXTRA_IMPORTED_AUDIO_URI = "imported_audio_uri";

    private ActivityVoiceTranscribeBinding binding;
    private VoiceTranscribeViewModel viewModel;
    private MediaPlayer mediaPlayer;
    private String importedAudioPath;
    private BaiduAsrHelper baiduAsrHelper;
    private BaiduRealtimeAsrHelper realtimeAsrHelper;

    private boolean pendingHoldAfterMicGrant;

    private final ActivityResultLauncher<String> recordPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                Toast.makeText(this, "麦克风权限已开启，请再次按住说话", Toast.LENGTH_SHORT).show();
                if (pendingHoldAfterMicGrant) {
                    pendingHoldAfterMicGrant = false;
                    startBaiduRealtimeRecognition();
                }
            } else {
                pendingHoldAfterMicGrant = false;
                Toast.makeText(this,
                    "未授予麦克风权限，无法语音转写。请到 设置→应用→云墨→权限 中开启麦克风",
                    Toast.LENGTH_LONG).show();
            }
        });

    private final ActivityResultLauncher<Intent> systemSpeechLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            ArrayList<String> matches = result.getData().getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                viewModel.appendSegment(matches.get(0));
                syncResultToView();
                Toast.makeText(this, R.string.voice_appended, Toast.LENGTH_SHORT).show();
            }
        });

    private final ActivityResultLauncher<String[]> audioPicker =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                importAudioUri(uri);
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVoiceTranscribeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(VoiceTranscribeViewModel.class);
        baiduAsrHelper = new BaiduAsrHelper();
        realtimeAsrHelper = new BaiduRealtimeAsrHelper(this);

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        updateStatusHint();
        setupObservers();
        setupHoldButton();
        setupActions();

        String uriStr = getIntent().getStringExtra(EXTRA_IMPORTED_AUDIO_URI);
        if (uriStr != null) {
            importAudioUri(Uri.parse(uriStr));
        }
    }

    private void updateStatusHint() {
        if (BaiduAsrConfig.isConfigured()) {
            binding.tvStatusHint.setText(R.string.voice_status_baidu);
            binding.cardImeVoice.setVisibility(View.VISIBLE);
            binding.tvImeHint.setText(R.string.voice_ime_hint_fallback);
        } else {
            binding.tvStatusHint.setText(R.string.voice_status_need_baidu);
            binding.cardImeVoice.setVisibility(View.VISIBLE);
            binding.tvImeHint.setText(R.string.voice_ime_hint_fallback);
        }
    }

    private void setupObservers() {
        viewModel.getResultText().observe(this, text -> {
            if (text == null) return;
            CharSequence cur = binding.etResult.getText();
            if (cur == null || !text.contentEquals(cur)) {
                binding.etResult.setText(text);
                binding.etResult.setSelection(text.length());
            }
        });

        viewModel.getLiveTranscript().observe(this, live -> {
            String show = (live == null || live.isEmpty())
                ? getString(R.string.voice_live_placeholder) : live;
            binding.tvLive.setText(show);
        });

        viewModel.getIsRecording().observe(this, recording -> {
            boolean rec = Boolean.TRUE.equals(recording);
            int color = rec ? 0xFFD32F2F : 0xFF1A73E8;
            binding.btnHoldSpeak.setBackgroundTintList(ColorStateList.valueOf(color));
            binding.btnHoldSpeak.setText(rec
                ? R.string.voice_release_to_send
                : R.string.voice_hold_to_speak);
        });

        viewModel.getVoiceFeedback().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupHoldButton() {
        binding.btnHoldSpeak.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    if (realtimeAsrHelper.isBusy()) {
                        Toast.makeText(VoiceTranscribeActivity.this,
                            "请等待上一段识别完成", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    startBaiduRealtimeRecognition();
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    // 停止识别
                    stopBaiduRealtimeRecognition();
                    return true;
                    
                default:
                    return false;
            }
        });
    }
    
    /**
     * 开始百度实时识别
     */
    private void startBaiduRealtimeRecognition() {
        if (!BaiduAsrConfig.isConfigured()) {
            Toast.makeText(this, R.string.voice_status_need_baidu, Toast.LENGTH_LONG).show();
            openKeyboardForVoiceInput();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingHoldAfterMicGrant = true;
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            binding.tvLive.setText("请允许麦克风权限后再次按住说话");
            return;
        }
        
        // 更新UI状态
        binding.btnHoldSpeak.setBackgroundTintList(ColorStateList.valueOf(0xFFD32F2F));
        binding.btnHoldSpeak.setText("松开发送");
        binding.tvLive.setText("正在录音...");
        
        realtimeAsrHelper.startRecording(wrapRealtimeCallback(new BaiduRealtimeAsrHelper.RealtimeCallback() {
            @Override
            public void onStart() {
                binding.tvLive.setText("🎤 正在录音，请说话（按住至少 1 秒）…");
            }

            @Override
            public void onVolumeChanged(int volume) {
                int display = Math.min(100, Math.max(volume, 8));
                binding.tvLive.setText("🎤 " + getVolumeBar(display) + " " + display + "%");
            }

            @Override
            public void onPartialResult(String text) {
                binding.tvLive.setText("识别中: " + text);
            }

            @Override
            public void onFinalResult(String text) {
                viewModel.appendSegment(text);
                syncResultToView();
                binding.tvLive.setText("✓ 识别完成: " + text);
                Toast.makeText(VoiceTranscribeActivity.this,
                    "✓ 已添加 " + text.length() + " 个字", Toast.LENGTH_SHORT).show();
                binding.tvLive.postDelayed(() ->
                    binding.tvLive.setText(getString(R.string.voice_live_placeholder)), 3000);
            }

            @Override
            public void onError(String error) {
                handleVoiceError(error);
            }
        }));
    }

    private BaiduRealtimeAsrHelper.RealtimeCallback wrapRealtimeCallback(
            BaiduRealtimeAsrHelper.RealtimeCallback inner) {
        return new BaiduRealtimeAsrHelper.RealtimeCallback() {
            @Override public void onStart() {
                runOnUiThread(() -> { if (!isFinishing()) inner.onStart(); });
            }
            @Override public void onVolumeChanged(int volume) {
                runOnUiThread(() -> { if (!isFinishing()) inner.onVolumeChanged(volume); });
            }
            @Override public void onPartialResult(String text) {
                runOnUiThread(() -> { if (!isFinishing()) inner.onPartialResult(text); });
            }
            @Override public void onFinalResult(String text) {
                runOnUiThread(() -> { if (!isFinishing()) inner.onFinalResult(text); });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> { if (!isFinishing()) inner.onError(error); });
            }
        };
    }

    private void handleVoiceError(String error) {
        if ("NEED_MIC_PERMISSION".equals(error)) {
            pendingHoldAfterMicGrant = true;
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            binding.tvLive.setText("请允许麦克风权限");
            return;
        }
        String msg = localizeVoiceError(error);
        binding.tvLive.setText("❌ " + msg);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        binding.btnHoldSpeak.setBackgroundTintList(ColorStateList.valueOf(0xFF1A73E8));
        binding.btnHoldSpeak.setText(R.string.voice_hold_to_speak);
        binding.tvLive.postDelayed(() ->
            binding.tvLive.setText(getString(R.string.voice_live_placeholder)), 3000);
    }
    
    /**
     * 停止百度实时识别
     */
    private void stopBaiduRealtimeRecognition() {
        realtimeAsrHelper.stopRecording();
        
        // 恢复UI状态
        binding.btnHoldSpeak.setBackgroundTintList(ColorStateList.valueOf(0xFF1A73E8));
        binding.btnHoldSpeak.setText(R.string.voice_hold_to_speak);
        binding.tvLive.setText("正在识别，请稍候...");
    }
    
    /**
     * 生成音量条
     */
    private String getVolumeBar(int volume) {
        int bars = volume / 10;  // 0-10个方块
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                sb.append("█");
            } else {
                sb.append("░");
            }
        }
        return sb.toString();
    }

    private void setupActions() {
        binding.btnKeyboardVoice.setOnClickListener(v -> openKeyboardForVoiceInput());
        binding.btnSystemSpeech.setOnClickListener(v -> launchSystemSpeech());
        binding.btnImportAudio.setOnClickListener(v ->
            audioPicker.launch(new String[]{"audio/*", "application/octet-stream"}));
        binding.btnToEditor.setOnClickListener(v -> {
            String text = binding.etResult.getText() != null
                ? binding.etResult.getText().toString().trim() : "";
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.voice_empty_to_editor, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, HandwriteEditorActivity.class);
            intent.putExtra(HandwriteEditorActivity.EXTRA_OCR_RESULT, text);
            startActivity(intent);
        });
        binding.btnPlayImported.setOnClickListener(v -> playImportedAudio());
        binding.btnRecognizeImported.setOnClickListener(v -> recognizeImportedAudio());
    }

    private void requestMicThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            viewModel.startRecording();
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void openKeyboardForVoiceInput() {
        binding.etResult.requestFocus();
        binding.etResult.setSelection(
            binding.etResult.getText() != null ? binding.etResult.getText().length() : 0);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etResult, InputMethodManager.SHOW_IMPLICIT);
        }
        Toast.makeText(this, 
            "输入法已打开\n\n使用方法：\n1. 点击搜狗输入法键盘上的麦克风图标🎤\n2. 说话后自动转为文字\n3. 点击发送即可添加到文本框", 
            Toast.LENGTH_LONG).show();
    }

    private void launchSystemSpeech() {
        Intent intent = SpeechInputHelper.createRecognizeIntent();
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, SpeechInputHelper.unavailableMessage(this), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            systemSpeechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开系统语音输入：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void syncResultToView() {
        String text = viewModel.getResultText().getValue();
        if (text != null) {
            binding.etResult.setText(text);
            binding.etResult.setSelection(text.length());
        }
    }

    private void importAudioUri(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String rawName = queryDisplayName(uri);
                final String displayName = (rawName == null || rawName.isEmpty())
                    ? "imported_audio" : rawName;
                File dir = new File(getFilesDir(), "audio/imports");
                if (!dir.exists() && !dir.mkdirs()) {
                    showToast("无法创建存储目录");
                    return;
                }
                File out = new File(dir, System.currentTimeMillis() + "_" + displayName);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) {
                        showToast("无法读取音频文件");
                        return;
                    }
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                    }
                }
                importedAudioPath = out.getAbsolutePath();
                runOnUiThread(() -> {
                    binding.cardImportedAudio.setVisibility(View.VISIBLE);
                    binding.tvImportedName.setText(displayName);
                    
                    Toast.makeText(this, "录音已导入\n点击「识别为文字」按钮可自动转换", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                showToast("导入失败: " + e.getMessage());
            }
        });
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(
            uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private void playImportedAudio() {
        if (importedAudioPath == null) return;
        releasePlayer();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(importedAudioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> releasePlayer());
        } catch (Exception e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void showToast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private void recognizeImportedAudio() {
        if (importedAudioPath == null) {
            Toast.makeText(this, "没有导入的录音文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showRecognitionProgress(true);
        setRecognitionProgress(5, "准备识别…", "正在读取音频文件…");
        
        binding.btnRecognizeImported.setEnabled(false);
        binding.btnPlayImported.setEnabled(false);
        
        baiduAsrHelper.recognizeAudioFile(importedAudioPath, new BaiduAsrHelper.AsrCallback() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    setRecognitionProgress(100, "识别完成", "100%");
                    binding.getRoot().postDelayed(() -> {
                        showRecognitionProgress(false);
                        binding.btnRecognizeImported.setEnabled(true);
                        binding.btnPlayImported.setEnabled(true);
                    }, 400);
                    viewModel.appendSegment(result);
                    syncResultToView();
                    showSuccessAnimation();
                    Toast.makeText(VoiceTranscribeActivity.this, 
                        "✓ 识别完成！已添加 " + result.length() + " 个字", 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    String msg = localizeVoiceError(error);
                    setRecognitionProgress(0, "识别失败", msg);
                    binding.getRoot().postDelayed(() -> {
                        showRecognitionProgress(false);
                        binding.btnRecognizeImported.setEnabled(true);
                        binding.btnPlayImported.setEnabled(true);
                    }, 600);
                    showErrorAnimation();
                    new android.app.AlertDialog.Builder(VoiceTranscribeActivity.this)
                        .setTitle("识别失败")
                        .setMessage(msg)
                        .setPositiveButton("知道了", null)
                        .setNeutralButton("查看帮助", (dialog, which) -> showRecognitionHelp())
                        .show();
                });
            }
        });
        
        setRecognitionProgress(25, "连接服务…", "正在获取百度语音令牌…");
        binding.getRoot().postDelayed(() -> {
            if (binding.layoutRecognitionProgress.getVisibility() == View.VISIBLE) {
                setRecognitionProgress(55, "上传音频…", "请保持网络畅通…");
            }
        }, 600);
        binding.getRoot().postDelayed(() -> {
            if (binding.layoutRecognitionProgress.getVisibility() == View.VISIBLE) {
                setRecognitionProgress(80, "识别中…", "AI 正在转写…");
            }
        }, 1500);
    }
    
    /**
     * 显示/隐藏识别进度指示器
     */
    private void showRecognitionProgress(boolean show) {
        binding.layoutRecognitionProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.layoutAudioButtons.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    /**
     * 更新识别状态文字
     */
    private void updateRecognitionStatus(String status, String tip) {
        binding.tvRecognitionStatus.setText(status);
        binding.tvRecognitionTip.setText(tip);
    }

    private void setRecognitionProgress(int percent, String status, String tip) {
        binding.progressRecognition.setIndeterminate(false);
        binding.progressRecognition.setProgress(Math.max(0, Math.min(100, percent)));
        updateRecognitionStatus(status, tip);
    }

    private static String localizeVoiceError(String error) {
        if (error == null) return "未知错误";
        if ("NEED_MIC_PERMISSION".equals(error)) {
            return "缺少麦克风权限，请到 设置→应用→云墨→权限 中开启麦克风";
        }
        if (BaiduApiErrors.isBaiduServicePermissionError(error)) {
            return error;
        }
        String lower = error.toLowerCase();
        if (lower.contains("record_audio")
            || lower.contains("microphone")
            || lower.contains("麦克风")) {
            return "缺少麦克风权限，请到 设置→应用→云墨→权限 中开启麦克风";
        }
        if (lower.contains("network") || lower.contains("timeout") || lower.contains("unable to resolve")) {
            return "网络异常，请检查 WiFi/移动数据后重试";
        }
        return error;
    }
    
    /**
     * 显示成功动画
     */
    private void showSuccessAnimation() {
        // 卡片闪烁效果
        binding.cardImportedAudio.setCardBackgroundColor(0xFFE8F5E9); // 浅绿色
        binding.cardImportedAudio.postDelayed(() -> {
            binding.cardImportedAudio.setCardBackgroundColor(0xFFFFFFFF); // 恢复白色
        }, 500);
        
        // 按钮图标变化
        binding.btnRecognizeImported.setIcon(
            ContextCompat.getDrawable(this, android.R.drawable.ic_menu_upload));
        binding.btnRecognizeImported.postDelayed(() -> {
            binding.btnRecognizeImported.setIcon(
                ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search));
        }, 1000);
    }
    
    /**
     * 显示错误动画
     */
    private void showErrorAnimation() {
        // 卡片闪烁效果
        binding.cardImportedAudio.setCardBackgroundColor(0xFFFFEBEE); // 浅红色
        binding.cardImportedAudio.postDelayed(() -> {
            binding.cardImportedAudio.setCardBackgroundColor(0xFFFFFFFF); // 恢复白色
        }, 500);
        
        // 震动效果
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(200);
            }
        } catch (Exception ignored) {
        }
    }
    
    /**
     * 显示识别帮助信息
     */
    private void showRecognitionHelp() {
        String helpText = "录音识别常见问题：\n\n" +
                "1. 未配置API密钥\n" +
                "   → 请参考「百度API配置说明.md」\n\n" +
                "2. 网络连接失败\n" +
                "   → 检查网络连接是否正常\n\n" +
                "3. 音频格式不支持\n" +
                "   → 支持WAV、M4A、AMR格式\n\n" +
                "4. 识别准确率低\n" +
                "   → 使用清晰的录音，减少背景噪音\n\n" +
                "5. 识别速度慢\n" +
                "   → 取决于网络速度和音频大小";
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("识别帮助")
            .setMessage(helpText)
            .setPositiveButton("知道了", null)
            .show();
    }

    @Override
    protected void onPause() {
        if (realtimeAsrHelper != null && realtimeAsrHelper.isRecording()) {
            stopBaiduRealtimeRecognition();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        try {
            if (realtimeAsrHelper != null) {
                realtimeAsrHelper.release();
            }
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
