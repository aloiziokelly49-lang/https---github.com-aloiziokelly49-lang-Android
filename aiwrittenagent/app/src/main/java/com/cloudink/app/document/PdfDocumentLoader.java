package com.cloudink.app.document;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import android.os.ParcelFileDescriptor;

/**
 * 将 content:// 等 URI 稳定打开为 {@link PdfRenderer}（必要时复制到应用缓存）。
 */
public final class PdfDocumentLoader {

    private static final String TAG = "PdfDocumentLoader";

    private PdfDocumentLoader() {}

    public static final class OpenResult implements AutoCloseable {
        public final PdfRenderer renderer;
        private ParcelFileDescriptor fd;
        private File tempFile;

        OpenResult(PdfRenderer renderer, ParcelFileDescriptor fd, File tempFile) {
            this.renderer = renderer;
            this.fd = fd;
            this.tempFile = tempFile;
        }

        public int getPageCount() {
            return renderer != null ? renderer.getPageCount() : 0;
        }

        @Override
        public void close() {
            if (renderer != null) {
                try {
                    renderer.close();
                } catch (Exception ignored) {
                }
            }
            if (fd != null) {
                try {
                    fd.close();
                } catch (Exception ignored) {
                }
                fd = null;
            }
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    Log.w(TAG, "temp pdf delete failed: " + tempFile.getAbsolutePath());
                }
                tempFile = null;
            }
        }
    }

    public static OpenResult open(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("文件地址为空");
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                File f = new File(path);
                if (f.exists() && f.canRead()) {
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(
                        f, ParcelFileDescriptor.MODE_READ_ONLY);
                    return new OpenResult(new PdfRenderer(fd), fd, null);
                }
            }
        }

        ParcelFileDescriptor pfd = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                try {
                    PdfRenderer renderer = new PdfRenderer(pfd);
                    return new OpenResult(renderer, pfd, null);
                } catch (IOException | IllegalArgumentException | SecurityException e) {
                    Log.w(TAG, "direct open failed, will copy to cache: " + e.getMessage());
                    try {
                        pfd.close();
                    } catch (Exception ignored) {
                    }
                    pfd = null;
                }
            }
        } catch (Exception e) {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception ignored) {
                }
            }
            Log.w(TAG, "openFileDescriptor failed: " + e.getMessage());
        }

        File cacheDir = new File(context.getCacheDir(), "pdf_import");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("无法创建 PDF 缓存目录");
        }
        File out = new File(cacheDir, "doc_" + System.currentTimeMillis() + ".pdf");
        copyUriToFile(context, uri, out);

        ParcelFileDescriptor cacheFd = ParcelFileDescriptor.open(
            out, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            PdfRenderer renderer = new PdfRenderer(cacheFd);
            return new OpenResult(renderer, cacheFd, out);
        } catch (Exception e) {
            cacheFd.close();
            if (out.exists()) out.delete();
            throw e;
        }
    }

    private static void copyUriToFile(Context context, Uri uri, File dest) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IOException("无法读取文件流");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }
        if (!dest.exists() || dest.length() < 4) {
            throw new IOException("PDF 文件为空或已损坏");
        }
    }
}
