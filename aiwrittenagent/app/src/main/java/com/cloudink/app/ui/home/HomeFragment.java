package com.cloudink.app.ui.home;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cloudink.app.R;
import com.cloudink.app.databinding.FragmentHomeBinding;
import com.cloudink.app.ocr.OcrRecognizer;
import com.cloudink.app.ocr.TesseractOcrManager;
import com.cloudink.app.ui.asr.VoiceTranscribeActivity;
import com.cloudink.app.ui.editor.HandwriteEditorActivity;
import com.cloudink.app.ui.document.DocumentViewerActivity;
import com.cloudink.app.ui.ocr.CameraOcrActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ProgressDialog ocrProgress;
    //单文件上传
    private final ActivityResultLauncher<String[]> docPicker =
        //选择单文件
        registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try {
                //获取持久化权限
                requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            //根据URI，路由到不同的处理界面（PDF、音频、图片、文本等）
            routeImportedDocument(uri);
        });

    //多文件上传
    private final ActivityResultLauncher<String[]> multiDocPicker =
        //选择多个文件
        registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            for (Uri u : uris) {
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                        u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
            }
            if (uris.size() == 1) {
                routeImportedDocument(uris.get(0));
            } else {
                // 多文件暂不区分类型，
                // 直接进入文档浏览器DocumentViewerActivity展示
                // DocumentViewerActivity会根据URI类型
                // 展示不同的预览界面

                // 将URI列表转换为字符串列表传递给DocumentViewerActivity
                ArrayList<String> list = new ArrayList<>();
                for (Uri u : uris) list.add(u.toString());
                //使用Intent传递URI列表到DocumentViewerActivity
                Intent intent = new Intent(requireContext(), DocumentViewerActivity.class);
                intent.putStringArrayListExtra(DocumentViewerActivity.EXTRA_URI_LIST, list);
                startActivity(intent);
                Toast.makeText(requireContext(),
                    getString(R.string.home_multi_import_toast, uris.size()),
                    Toast.LENGTH_LONG).show();
            }
        });

    private final ActivityResultLauncher<String> imagePicker =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            runOcrInBackground(uri);
        });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupCardListeners();
    }

    // 根据URI类型，路由到不同的处理界面（PDF、音频、图片、文本等）
    private void routeImportedDocument(Uri uri) {
        // 解析URI类型，判断是PDF、音频、图片还是文本
        ImportType kind = resolveImportType(uri);
        switch (kind) {
            case AUDIO: {
                // 如果是音频文件，直接打开语音转写界面，并传入URI
                Intent intent = new Intent(requireContext(), VoiceTranscribeActivity.class);
                intent.putExtra(VoiceTranscribeActivity.EXTRA_IMPORTED_AUDIO_URI, uri.toString());
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
                Toast.makeText(requireContext(), R.string.home_import_audio_toast, Toast.LENGTH_LONG).show();
                break;
            }
            case PDF:
            case IMAGE:
            case TEXT: {
                // 如果是PDF、图片或文本，直接打开文档浏览界面，并传入URI
                Intent intent = new Intent(requireContext(), DocumentViewerActivity.class);
                intent.setData(uri);
                intent.putExtra(DocumentViewerActivity.EXTRA_URI, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
                int msg = kind == ImportType.PDF ? R.string.home_import_pdf_toast
                    : kind == ImportType.IMAGE ? R.string.home_import_image_toast
                    : R.string.home_import_text_toast;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                break;
            }
            default:
                Toast.makeText(requireContext(),
                    "暂不支持该格式，请选择 PDF、音频、图片或文本", Toast.LENGTH_LONG).show();
        }
    }

    //判断URI类型，先通过ContentResolver获取MIME类型，
    //如果无法获取或不明确，则通过文件扩展名进行判断
    private ImportType resolveImportType(Uri uri) {
        String type = requireContext().getContentResolver().getType(uri);
        if (type != null) {
            //根据MIME类型判断文件类型
            if (type.startsWith("audio/")) return ImportType.AUDIO;
            if ("application/pdf".equals(type)) return ImportType.PDF;
            if (type.startsWith("image/")) return ImportType.IMAGE;
            if (type.startsWith("text/")) return ImportType.TEXT;
        }
        String name = queryDisplayName(uri);
        if (name != null) {
            String lower = name.toLowerCase();
            //根据文件扩展名判断文件类型
            if (lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav")
                || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".flac")
                || lower.endsWith(".amr") || lower.endsWith(".3gp")) {
                return ImportType.AUDIO;
            }
            if (lower.endsWith(".pdf")) return ImportType.PDF;
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
                return ImportType.IMAGE;
            }
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
                return ImportType.TEXT;
            }
        }
        return ImportType.UNKNOWN;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = requireContext().getContentResolver().query(
            uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private void setupCardListeners() {
        binding.cardHandwrite.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), HandwriteEditorActivity.class)));

        binding.cardImport.setOnClickListener(v ->
            multiDocPicker.launch(new String[]{
                "application/pdf",
                "audio/*",
                "text/*",
                "image/*",
                "application/octet-stream"
            }));

        binding.cardOcr.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), CameraOcrActivity.class)));
        binding.cardOcr.setOnLongClickListener(v -> {
            imagePicker.launch("image/*");
            return true;
        });

        binding.cardAsr.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), VoiceTranscribeActivity.class)));

        binding.cardSplitReader.setOnClickListener(v -> {
            // 功能点4已取消：双栏分屏阅读功能已移除
            // 现在直接打开文档浏览器查看演示PDF
            Intent intent = new Intent(requireContext(), DocumentViewerActivity.class);
            intent.putExtra(DocumentViewerActivity.EXTRA_SHOW_DEMO, true);
            startActivity(intent);
        });

        binding.cardHistory.setOnClickListener(v ->
            startActivity(new Intent(requireContext(),
                com.cloudink.app.ui.history.HistoryActivity.class)));
    }

    private void runOcrInBackground(Uri imageUri) {
        showOcrProgress(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                android.graphics.Bitmap bmp = decodeOcrBitmap(imageUri);
                if (bmp == null) {
                    showOcrError("无法读取图片");
                    return;
                }
                OcrRecognizer.recognize(requireContext(), bmp, true, new OcrRecognizer.Callback() {
                    @Override
                    public void onSuccess(String text, boolean fromBaidu) {
                        bmp.recycle();
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            showOcrProgress(false);
                            Intent intent = new Intent(requireContext(), HandwriteEditorActivity.class);
                            intent.putExtra(HandwriteEditorActivity.EXTRA_OCR_RESULT, text);
                            startActivity(intent);
                            String hint = fromBaidu
                                ? "百度手写识别完成，可继续编辑"
                                : getString(R.string.ocr_low_confidence_hint);
                            Toast.makeText(requireContext(), hint, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        bmp.recycle();
                        showOcrError(error);
                    }
                });
            } catch (Exception e) {
                showOcrError("OCR 失败: " + e.getMessage());
            }
        });
    }

    @androidx.annotation.Nullable
    private android.graphics.Bitmap decodeOcrBitmap(Uri imageUri) throws java.io.IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream probe = requireContext().getContentResolver().openInputStream(imageUri)) {
            if (probe == null) return null;
            BitmapFactory.decodeStream(probe, null, bounds);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888;
        opts.inSampleSize = TesseractOcrManager.computeInSampleSize(
            bounds.outWidth, bounds.outHeight, 2048);
        try (InputStream is = requireContext().getContentResolver().openInputStream(imageUri)) {
            if (is == null) return null;
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private void showOcrProgress(boolean show) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (show) {
                if (ocrProgress == null) {
                    ocrProgress = new ProgressDialog(requireContext());
                    ocrProgress.setMessage("正在识别文字…");
                    ocrProgress.setCancelable(false);
                }
                if (!ocrProgress.isShowing()) ocrProgress.show();
            } else if (ocrProgress != null && ocrProgress.isShowing()) {
                ocrProgress.dismiss();
            }
        });
    }

    private void showOcrError(String msg) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            showOcrProgress(false);
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDestroyView() {
        if (ocrProgress != null && ocrProgress.isShowing()) {
            ocrProgress.dismiss();
        }
        ocrProgress = null;
        super.onDestroyView();
        binding = null;
    }
}
