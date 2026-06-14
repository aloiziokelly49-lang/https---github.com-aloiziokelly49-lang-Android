package com.cloudink.app.ui.ocr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.cloudink.app.R;
import com.cloudink.app.databinding.ActivityCameraOcrBinding;
import com.cloudink.app.data.repository.CloudInkRepository;
import com.cloudink.app.ocr.OcrRecognizer;
import com.cloudink.app.ui.editor.HandwriteEditorActivity;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class CameraOcrActivity extends AppCompatActivity {

    private ActivityCameraOcrBinding binding;
    private ImageCapture imageCapture;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera();
            else {
                Toast.makeText(this, R.string.camera_need_permission, Toast.LENGTH_LONG).show();
                finish();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCameraOcrBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnCapture.setOnClickListener(v -> captureAndRecognize());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // 启动 CameraX 预览和拍照功能
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();// 优先使用低延迟模式，适合 OCR 场景
                provider.unbindAll();

                // 绑定生命周期和用例，默认使用后置摄像头
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    //拍照后，启动新线程进行 OCR 识别，
    //识别完成后跳转到 HandwriteEditorActivity 显示结果
    private void captureAndRecognize() {
        if (imageCapture == null) return;
        binding.btnCapture.setEnabled(false);
        binding.tvHint.setText(R.string.camera_recognizing);

        File photo = new File(getCacheDir(), "ocr_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts =
            new ImageCapture.OutputFileOptions.Builder(photo).build();
        //拍照并保存到临时文件，成功后进行 OCR 识别，失败则提示错误并重置 UI
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(this),
            new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                    android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeFile(photo.getAbsolutePath());
                    if (bmp == null) {
                        resetUi();
                        return;
                    }
                    //拍照成功后，启动后台线程进行 OCR 识别，
                    Executors.newSingleThreadExecutor().execute(() ->
                        //使用 OcrRecognizer（自定义） 进行 OCR 识别，
                        OcrRecognizer.recognize(CameraOcrActivity.this, bmp, true,
                            new OcrRecognizer.Callback() {
                                @Override
                                public void onSuccess(String text, boolean fromBaidu) {
                                    bmp.recycle();
                                    //识别成功后，保存草稿并跳转到 HandwriteEditorActivity 显示结果
                                    runOnUiThread(() -> {
                                        //使用 CloudInkRepository（自定义）的 saveDraft 方法保存草稿
                                        CloudInkRepository.get(CameraOcrActivity.this)
                                            .saveDraft(text, "OCR", null);
                                        //跳转到 HandwriteEditorActivity 显示识别结果
                                        Intent intent = new Intent(CameraOcrActivity.this,
                                            HandwriteEditorActivity.class);
                                        intent.putExtra(HandwriteEditorActivity.EXTRA_OCR_RESULT, text);
                                        startActivity(intent);
                                        finish();
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    bmp.recycle();
                                    runOnUiThread(() -> {
                                        Toast.makeText(CameraOcrActivity.this,
                                            error, Toast.LENGTH_LONG).show();
                                        resetUi();
                                    });
                                }
                            }));
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    Toast.makeText(CameraOcrActivity.this,
                        exception.getMessage(), Toast.LENGTH_LONG).show();
                    resetUi();
                }
            });
    }

    private void resetUi() {
        binding.btnCapture.setEnabled(true);
        binding.tvHint.setText(R.string.camera_hint);
    }
}
