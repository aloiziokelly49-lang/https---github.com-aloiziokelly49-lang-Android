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

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndRecognize() {
        if (imageCapture == null) return;
        binding.btnCapture.setEnabled(false);
        binding.tvHint.setText(R.string.camera_recognizing);

        File photo = new File(getCacheDir(), "ocr_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts =
            new ImageCapture.OutputFileOptions.Builder(photo).build();
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
                    Executors.newSingleThreadExecutor().execute(() ->
                        OcrRecognizer.recognize(CameraOcrActivity.this, bmp, true,
                            new OcrRecognizer.Callback() {
                                @Override
                                public void onSuccess(String text, boolean fromBaidu) {
                                    bmp.recycle();
                                    runOnUiThread(() -> {
                                        CloudInkRepository.get(CameraOcrActivity.this)
                                            .saveDraft(text, "OCR", null);
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
