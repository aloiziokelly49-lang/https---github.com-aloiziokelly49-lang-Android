package com.cloudink.app.ui.export;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.cloudink.app.R;
import com.cloudink.app.databinding.ActivityExportPreviewBinding;
import com.cloudink.app.rendering.HandwritingEngine;
import com.cloudink.app.rendering.model.HandwritingParams;
import com.cloudink.app.rendering.model.PenType;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 高清大图预览与导出页 —— 分块渲染防 OOM + MediaStore 相册保存 + PdfDocument 导出。
 *
 */
@RequiresApi(api = Build.VERSION_CODES.Q)
public class ExportPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_TEXT    = "export_text";
    public static final String EXTRA_CHAR_SP = "export_char_sp";
    public static final String EXTRA_LINE_SP = "export_line_sp";
    public static final String EXTRA_JITTER  = "export_jitter";
    public static final String EXTRA_PAPER   = "export_paper";
    public static final String EXTRA_PEN     = "export_pen";
    public static final String EXTRA_FONT    = "export_font";

    private static final int A4_W = 2480;
    private static final int A4_H = 3508;
    private static final int TILE_MAX_H = 4096;
    private static final int EXPORT_W = 1240; 

    private ActivityExportPreviewBinding binding;
    private final HandwritingEngine engine = com.cloudink.app.CloudInkApplication
        .getInstance().getHandwritingEngine();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ProgressDialog progressDialog;

    private String text;
    private HandwritingParams params;
    private PenType penType;
    private Bitmap finalBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityExportPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        readIntent();
        setupButtons();
        startExport();
    }

    // ================================================================
    // 读取参数
    // ================================================================

    private void readIntent() {
        Intent i = getIntent();
        text = i.getStringExtra(EXTRA_TEXT);
        if (text == null || text.isEmpty()) text = "无导出内容";

        params = new HandwritingParams();
        params.setCharSpacing(i.getFloatExtra(EXTRA_CHAR_SP, 0.5f));
        params.setLineSpacing(i.getFloatExtra(EXTRA_LINE_SP, 1.6f));
        params.setJitterThreshold(i.getFloatExtra(EXTRA_JITTER, 0.35f));
        params.setPaperIndex(i.getIntExtra(EXTRA_PAPER, 0));
        params.setPenType(i.getStringExtra(EXTRA_PEN) != null
            ? i.getStringExtra(EXTRA_PEN) : "fountain");
        params.setFontPath(i.getStringExtra(EXTRA_FONT) != null
            ? i.getStringExtra(EXTRA_FONT) : "fonts/NiHeWoDeLangManYuZhou-2.ttf");

        engine.switchFont(this, params.getFontPath());
        engine.setTheme(com.cloudink.app.rendering.model.PaperThemeManager
            .fromIndex(params.getPaperIndex()));

        try { penType = PenType.valueOf(params.getPenType().toUpperCase()); }
        catch (IllegalArgumentException e) { penType = PenType.FOUNTAIN; }
    }

    // ================================================================
    // 按钮
    // ================================================================

    private void setupButtons() {
        binding.btnSaveGallery.setOnClickListener(v -> saveToGallery());
        binding.btnExportPdf.setOnClickListener(v -> exportPdf());
    }

    // ================================================================
    // 导出主流程 (后台线程) + LoadingDialog
    // ================================================================

    private void startExport() {
        showLoading("正在渲染排版...");
        binding.btnSaveGallery.setEnabled(false);
        binding.btnExportPdf.setEnabled(false);

        executor.submit(() -> {
            // 分块渲染文本为多张 Bitmap
            List<Bitmap> tiles = renderTiles();
            // 将多张 Bitmap 拼接成一张 A4 大小的 Bitmap
            finalBitmap = stitchToA4(tiles);

            runOnUiThread(() -> {
                dismissLoading();
                binding.zoomPreview.setImageBitmap(finalBitmap);
                binding.btnSaveGallery.setEnabled(true);
                binding.btnExportPdf.setEnabled(true);
            });
        });
    }

    private void showLoading(String msg) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }
        progressDialog.setMessage(msg);
        progressDialog.show();
    }

    private void dismissLoading() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // ================================================================
    // 分块渲染流程
    // ================================================================

    private List<Bitmap> renderTiles() {
        List<Bitmap> tiles = new ArrayList<>();
        // 将文本按换行符分割成段落，
        String[] paragraphs = text.split("\n", -1);

        // 再按每 15 段分成一组，用来渲染成一个 Bitmap，
        final int groupSize = 15;
        for (int i = 0; i < paragraphs.length; i += groupSize) {
            StringBuilder chunk = new StringBuilder();
            for (int j = i; j < Math.min(i + groupSize, paragraphs.length); j++) {
                if (chunk.length() > 0) chunk.append('\n');
                chunk.append(paragraphs[j]);
            }
            if (chunk.length() == 0 && paragraphs.length == 0) {
                chunk.append(" ");
            }
            // 计算导出专用参数，
            // 来把 bitmap 宽度固定在A4宽度
            HandwritingParams exportParams = buildExportParams();

            // 渲染当前文本块，得到 Bitmap，
            Bitmap tile = engine.render(chunk.toString(), exportParams, penType, EXPORT_W);
            if (tile != null) tiles.add(tile);
        }
        return tiles;
    }

    /** 构建导出专用参数 (按 EXPORT_W 缩放字号)。 */
    private HandwritingParams buildExportParams() {
        HandwritingParams ep = new HandwritingParams();
        // 字号按宽度比例缩放: 720→1240 约 1.72x
        float scale = (float) EXPORT_W / 720f;
        ep.setTextSize(params.getTextSize() * scale);
        ep.setCharSpacing(params.getCharSpacing());
        ep.setLineSpacing(params.getLineSpacing());
        ep.setJitterThreshold(params.getJitterThreshold());
        ep.setPaperIndex(params.getPaperIndex());
        ep.setPenType(params.getPenType());
        return ep;
    }

    // ================================================================
    // A4 拼接
    // ================================================================

    private Bitmap stitchToA4(List<Bitmap> tiles) {
        // 创建一张 A4 大小的 Bitmap 作为画布，
        Bitmap a4 = Bitmap.createBitmap(A4_W, A4_H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(a4);
        // 使用与编辑器相同的纸张主题色，保持导出一致
        int paperColor = com.cloudink.app.rendering.model.PaperThemeManager
            .fromIndex(params.getPaperIndex()).getPaperColor();
        canvas.drawColor(paperColor);

        int yCursor = 0;
        for (Bitmap tile : tiles) {
            if (tile == null || tile.isRecycled()) continue;

            // 缩放 tile 至 A4 宽度
            float tileScale = (float) A4_W / tile.getWidth();
            int scaledH = (int) (tile.getHeight() * tileScale);
            if (yCursor + scaledH > A4_H) scaledH = A4_H - yCursor; // 裁剪超出的部分

            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postScale(tileScale, tileScale);
            m.postTranslate(0, yCursor);

            canvas.save();
            canvas.clipRect(0, yCursor, A4_W, yCursor + scaledH);
            canvas.drawBitmap(tile, m, null);
            canvas.restore();

            yCursor += scaledH;
            tile.recycle(); // 拼接完立刻回收 tile
            if (yCursor >= A4_H) break;
        }

        return a4;
    }

    // ================================================================
    // 保存至相册 (MediaStore, Android 10+)
    // ================================================================

    private void saveToGallery() {
        if (finalBitmap == null || finalBitmap.isRecycled()) {
            Toast.makeText(this, "图片尚未渲染完成", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading("正在保存到相册...");

        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "CloudInk_" + ts + ".png";
            
            // 构建 ContentValues，准备插入一条新的媒体记录，
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/CloudInk");

            // 标记为 pending，表示正在写入数据，
            // 不会在相册里显示，也不会让其他应用访问，
            // 直到我们写入完成并更新状态，
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            // 插入一条新的媒体记录，也就是生成一个占位，返回Uri，
            // 获取到这个占位的 Uri 后，才能打开输出流写入数据，
            Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, "创建媒体记录失败", Toast.LENGTH_SHORT).show();
                dismissLoading();
                return;
            }

            // 打开这个 Uri 的输出流，写入 Bitmap 数据，
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            values.clear();

            // 写入完成，更新状态，标记为非 pending，
            // 这样就可以在相册里显示了，
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);

            Toast.makeText(this, "已保存至 Pictures/CloudInk", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        dismissLoading();
    }

    // ================================================================
    // 导出 PDF
    // ================================================================

    private void exportPdf() {
        if (finalBitmap == null || finalBitmap.isRecycled()) {
            Toast.makeText(this, "图片尚未渲染完成", Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading("正在导出 PDF...");

        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "CloudInk_" + ts + ".pdf";

            // 构建 ContentValues，准备插入一条新的媒体记录，
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CloudInk");
            // 标记为 pending，表示正在写入数据，
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            // 插入一条新的媒体记录，也就是创建一个占位，
            // 返回 Uri，
            // 获取到这个 Uri 后，才能打开输出流写入数据，
            Uri uri = getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, "创建文件记录失败", Toast.LENGTH_SHORT).show();
                return;
            }

            //使用 PdfDocument
            PdfDocument pdf = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(A4_W, A4_H, 1).create();
            // 创建一个 PDF 页面
            PdfDocument.Page page = pdf.startPage(pageInfo);
            // 将 Bitmap 绘制到 PDF 页面上，
            page.getCanvas().drawBitmap(finalBitmap, 0, 0, null);
            // 完成当前页面的绘制，并将其添加到 PDF 文档中，
            pdf.finishPage(page);

            // 打开这个 Uri 的输出流，写入 PDF 数据，
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                pdf.writeTo(out);
            }
            pdf.close();

            values.clear();

            // 写入完成，更新状态，标记为非 pending，
            // 这样就可以在下载管理器里显示了，
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);

            Toast.makeText(this, "PDF 已导出至 Downloads/CloudInk", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "PDF 导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        dismissLoading();
    }

    @Override
    protected void onDestroy() {
        dismissLoading();
        super.onDestroy();
        executor.shutdownNow();
        engine.dispose();
        if (finalBitmap != null && !finalBitmap.isRecycled()) {
            finalBitmap.recycle();
        }
        binding = null;
    }
}
