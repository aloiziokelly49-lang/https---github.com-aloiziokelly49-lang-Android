package com.cloudink.app.ui.document;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cloudink.app.R;
import com.cloudink.app.databinding.ActivityDocumentViewerBinding;
import com.cloudink.app.document.PdfDocumentLoader;
import com.cloudink.app.document.PdfViewerAdapter;
import com.cloudink.app.ocr.OcrRecognizer;
import com.cloudink.app.ocr.TesseractOcrManager;
import com.cloudink.app.ui.editor.HandwriteEditorActivity;
import com.cloudink.app.ui.home.HomeActivity;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class DocumentViewerActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_URI_LIST = "extra_uri_list";
    public static final String EXTRA_SHOW_DEMO = "extra_show_demo";

    private ActivityDocumentViewerBinding binding;
    private PdfViewerAdapter pdfAdapter;
    private PdfDocumentLoader.OpenResult pdfOpenResult;
    private String pendingEditorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDocumentViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnToEditor.setOnClickListener(v -> {
            if (pendingEditorText == null || pendingEditorText.isEmpty()) return;
            Intent intent = new Intent(this, HandwriteEditorActivity.class);
            intent.putExtra(HandwriteEditorActivity.EXTRA_OCR_RESULT, pendingEditorText);
            startActivity(intent);
        });

        pdfAdapter = new PdfViewerAdapter();
        binding.rvPdfPages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPdfPages.setAdapter(pdfAdapter);

        handleIntent();
    }

    private void handleIntent() {
        ArrayList<String> uriStrings = getIntent().getStringArrayListExtra(EXTRA_URI_LIST);
        if (uriStrings != null && !uriStrings.isEmpty()) {
            openUri(Uri.parse(uriStrings.get(0)));
            return;
        }
        Uri uri = getIntent().getParcelableExtra(EXTRA_URI);
        if (uri == null) uri = getIntent().getData();
        if (uri != null) {
            openUri(uri);
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_SHOW_DEMO, false)) {
            File demo = new File(getExternalFilesDir(null), HomeActivity.DEMO_PDF_NAME);
            if (demo.exists()) openUri(Uri.fromFile(demo));
        }
    }

    private void openUri(Uri uri) {
        binding.progress.setVisibility(View.VISIBLE);
        String type = getContentResolver().getType(uri);
        String name = queryDisplayName(uri);
        String lower = name != null ? name.toLowerCase() : "";

        if (isPdf(type, lower)) {
            loadPdf(uri, name);
        } else if (isText(type, lower)) {
            loadText(uri, name);
        } else if (isImage(type, lower)) {
            loadImage(uri, name);
        } else {
            loadPdf(uri, name);
        }
    }

    private void loadPdf(Uri uri, String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                PdfDocumentLoader.OpenResult opened = PdfDocumentLoader.open(this, uri);
                int pages = opened.getPageCount();
                String title = name != null && !name.isEmpty() ? name : "PDF";
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        opened.close();
                        return;
                    }
                    binding.progress.setVisibility(View.GONE);
                    binding.rvPdfPages.setVisibility(View.VISIBLE);
                    closePdf();
                    pdfOpenResult = opened;
                    pdfAdapter.setPdfRenderer(opened.renderer);
                    pdfAdapter.notifyDataSetChanged();
                    binding.toolbar.setTitle(title);
                    binding.toolbar.setSubtitle(pages + " 页");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, "PDF 打开失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadText(Uri uri, String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) throw new IllegalStateException("无法读取");
                String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    binding.scrollText.setVisibility(View.VISIBLE);
                    binding.toolbar.setTitle(name != null ? name : "文本");
                    binding.tvTextContent.setText(text);
                    pendingEditorText = text;
                    binding.btnToEditor.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadImage(Uri uri, String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                android.graphics.Bitmap bmp = BitmapFactory.decodeStream(is);
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    if (bmp == null) {
                        Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    binding.scrollImage.setVisibility(View.VISIBLE);
                    binding.toolbar.setTitle(name != null ? name : "图片");
                    binding.ivImage.setImageBitmap(bmp);
                    binding.btnToEditor.setVisibility(View.VISIBLE);
                    binding.btnToEditor.setText(R.string.document_ocr_to_editor);
                    binding.btnToEditor.setOnClickListener(v -> runOcr(uri));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void runOcr(Uri uri) {
        binding.progress.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                android.graphics.Bitmap bmp = decodeBitmap(uri);
                if (bmp == null) {
                    runOnUiThread(() -> binding.progress.setVisibility(View.GONE));
                    return;
                }
                OcrRecognizer.recognize(this, bmp, true, new OcrRecognizer.Callback() {
                    @Override
                    public void onSuccess(String text, boolean fromBaidu) {
                        bmp.recycle();
                        runOnUiThread(() -> {
                            binding.progress.setVisibility(View.GONE);
                            Intent intent = new Intent(DocumentViewerActivity.this,
                                HandwriteEditorActivity.class);
                            intent.putExtra(HandwriteEditorActivity.EXTRA_OCR_RESULT, text);
                            startActivity(intent);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        bmp.recycle();
                        runOnUiThread(() -> {
                            binding.progress.setVisibility(View.GONE);
                            Toast.makeText(DocumentViewerActivity.this, error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private android.graphics.Bitmap decodeBitmap(Uri uri) throws java.io.IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream probe = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(probe, null, bounds);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = TesseractOcrManager.computeInSampleSize(
            bounds.outWidth, bounds.outHeight, 2048);
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private static boolean isPdf(String type, String lower) {
        return "application/pdf".equals(type) || lower.endsWith(".pdf");
    }

    private static boolean isText(String type, String lower) {
        return (type != null && type.startsWith("text/"))
            || lower.endsWith(".txt") || lower.endsWith(".md");
    }

    private static boolean isImage(String type, String lower) {
        return (type != null && type.startsWith("image/"))
            || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    private String queryDisplayName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(
            uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private void closePdf() {
        pdfAdapter.detachRenderer();
        if (pdfOpenResult != null) {
            try { pdfOpenResult.close(); } catch (Exception ignored) {}
            pdfOpenResult = null;
        }
    }

    @Override
    protected void onDestroy() {
        closePdf();
        super.onDestroy();
    }
}
