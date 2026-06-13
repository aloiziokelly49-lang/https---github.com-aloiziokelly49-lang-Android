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

    // 多文档导航
    private ArrayList<String> documentUriStrings;
    private int currentDocumentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDocumentViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 多文档导航按钮
        binding.btnPrev.setOnClickListener(v -> showPreviousDocument());
        binding.btnNext.setOnClickListener(v -> showNextDocument());

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
            documentUriStrings = uriStrings;
            currentDocumentIndex = 0;
            updateNavigationButtons();
            openUri(Uri.parse(documentUriStrings.get(currentDocumentIndex)));
            return;
        }

        // 单文件：用 EXTRA_URI 或 getData()
        Uri uri = getIntent().getParcelableExtra(EXTRA_URI);
        if (uri == null) uri = getIntent().getData();
        if (uri != null) {
            documentUriStrings = new ArrayList<>();
            documentUriStrings.add(uri.toString());
            currentDocumentIndex = 0;
            updateNavigationButtons();
            openUri(uri);
            return;
        }

        // 演示 PDF
        if (getIntent().getBooleanExtra(EXTRA_SHOW_DEMO, false)) {
            File demo = new File(getExternalFilesDir(null), HomeActivity.DEMO_PDF_NAME);
            if (demo.exists()) {
                documentUriStrings = new ArrayList<>();
                documentUriStrings.add(Uri.fromFile(demo).toString());
                currentDocumentIndex = 0;
                updateNavigationButtons();
                openUri(Uri.fromFile(demo));
            }
        }
    }

    // ================================================================
    // 多文档导航
    // ================================================================

    private void showNextDocument() {
        if (documentUriStrings == null
            || currentDocumentIndex >= documentUriStrings.size() - 1) return;
        closeCurrentDocument();
        currentDocumentIndex++;
        updateNavigationButtons();
        openUri(Uri.parse(documentUriStrings.get(currentDocumentIndex)));
    }

    private void showPreviousDocument() {
        if (documentUriStrings == null || currentDocumentIndex <= 0) return;
        closeCurrentDocument();
        currentDocumentIndex--;
        updateNavigationButtons();
        openUri(Uri.parse(documentUriStrings.get(currentDocumentIndex)));
    }

    /** 切换到下一个文档前清理当前视图。 */
    private void closeCurrentDocument() {
        closePdf();
        binding.rvPdfPages.setVisibility(View.GONE);
        binding.scrollText.setVisibility(View.GONE);
        binding.scrollImage.setVisibility(View.GONE);
        binding.btnToEditor.setVisibility(View.GONE);
        binding.progress.setVisibility(View.VISIBLE);
        pendingEditorText = null;
    }

    /** 刷新导航按钮的可见性与启用状态，并更新标题栏备注。 */
    private void updateNavigationButtons() {
        boolean hasMultiple = documentUriStrings != null && documentUriStrings.size() > 1;
        binding.navButtons.setVisibility(hasMultiple ? View.VISIBLE : View.GONE);
        if (hasMultiple) {
            binding.btnPrev.setEnabled(currentDocumentIndex > 0);
            binding.btnNext.setEnabled(currentDocumentIndex < documentUriStrings.size() - 1);
            binding.tvNavTitle.setText(
                "文档 " + (currentDocumentIndex + 1) + " / " + documentUriStrings.size());
        }
    }

    /** 构建副标题：多文档时附加 "文档 X/Y · " 前缀。 */
    private String buildSubtitle(String detail) {
        if (documentUriStrings != null && documentUriStrings.size() > 1) {
            return "文档 " + (currentDocumentIndex + 1) + " / "
                + documentUriStrings.size() + " · " + detail;
        }
        return detail;
    }

    // ================================================================
    // 文档打开
    // ================================================================

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
                    binding.toolbar.setSubtitle(buildSubtitle(pages + " 页"));
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
                    binding.toolbar.setSubtitle(buildSubtitle(""));
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
                    binding.toolbar.setSubtitle(buildSubtitle(""));
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
