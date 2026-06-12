package com.cloudink.app.document;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudink.app.R;

/**
 * 基于 Android 原生 {@link PdfRenderer} 的 PDF 逐页渲染适配器。
 * <p>
 * 依赖: 零第三方 PDF 库, 纯系统 API。适用于纵向连续滑动浏览。
 * <p>
 * 内存策略:
 * <ul>
 *   <li>onBindViewHolder: 打开 PdfRenderer.Page → 渲染到 Bitmap → 关闭 Page</li>
 *   <li>onViewRecycled: recycle Bitmap, 防止内存泄漏</li>
 *   <li>每页 Bitmap 宽度 = RecyclerView 宽度, 高度按 PDF 页面比例自动计算</li>
 * </ul>
 */
public class PdfViewerAdapter extends RecyclerView.Adapter<PdfViewerAdapter.PageViewHolder> {

    private static final String TAG = "PdfViewerAdapter";
    private PdfRenderer pdfRenderer;
    private int viewWidth;
    private OnPageLongClickListener longClickListener;

    public PdfViewerAdapter() {
        this.viewWidth = 720; // 默认宽度, 在 onAttachedToRecyclerView 时更新
        Log.d(TAG, "PdfViewerAdapter 创建，默认宽度: " + viewWidth);
    }

    /** 设置 PDF 数据源（生命周期由 Activity 管理，此处不 close）。 */
    public void setPdfRenderer(PdfRenderer renderer) {
        this.pdfRenderer = renderer;
        if (renderer != null) {
            Log.d(TAG, "设置 PdfRenderer，页数: " + renderer.getPageCount());
        }
    }

    /** 解除引用，避免使用已关闭的 renderer。 */
    public void detachRenderer() {
        this.pdfRenderer = null;
        notifyDataSetChanged();
        Log.d(TAG, "detachRenderer");
    }

    public PdfRenderer getPdfRenderer() {
        return pdfRenderer;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        super.onAttachedToRecyclerView(rv);
        Log.d(TAG, "onAttachedToRecyclerView 被调用");
        
        // 立即获取宽度，如果为0则使用默认值
        int width = rv.getWidth();
        Log.d(TAG, "RecyclerView 当前宽度: " + width);
        
        if (width > 0) {
            viewWidth = width;
            Log.d(TAG, "使用 RecyclerView 宽度: " + viewWidth);
        } else {
            // 等待布局完成后获取实际宽度
            rv.post(() -> {
                int w = rv.getWidth();
                Log.d(TAG, "布局完成后 RecyclerView 宽度: " + w);
                if (w > 0) {
                    viewWidth = w;
                    Log.d(TAG, "更新 viewWidth 为: " + viewWidth + "，重新渲染");
                    notifyDataSetChanged(); // 重新渲染
                } else {
                    Log.w(TAG, "布局完成后宽度仍为0，使用默认宽度: " + viewWidth);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        int count = pdfRenderer != null ? pdfRenderer.getPageCount() : 0;
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder 被调用");
        View item = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_pdf_page, parent, false);
        return new PageViewHolder(item);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder 开始渲染第 " + position + " 页");
        
        if (pdfRenderer == null) {
            Log.e(TAG, "pdfRenderer 为 null，无法渲染");
            return;
        }

        try {
            // 获取实际可用宽度
            int w = holder.itemView.getWidth();
            Log.d(TAG, "itemView 宽度: " + w);
            
            if (w <= 0) {
                // 如果itemView宽度还没确定，使用父容器宽度
                ViewGroup parent = (ViewGroup) holder.itemView.getParent();
                if (parent != null) {
                    w = parent.getWidth();
                    Log.d(TAG, "使用父容器宽度: " + w);
                }
            }
            if (w <= 0) {
                w = viewWidth;
                Log.d(TAG, "使用 viewWidth: " + w);
            }
            if (w <= 0) {
                w = 720;
                Log.d(TAG, "使用默认宽度: " + w);
            }

            // 打开PDF页面
            PdfRenderer.Page page = pdfRenderer.openPage(position);
            Log.d(TAG, "PDF 页面 " + position + " 原始尺寸: " + page.getWidth() + "x" + page.getHeight());
            
            int pageW = w;
            // 修正高度计算，使用正确的宽度比例
            int pageH = (int) (page.getHeight() * ((float) pageW / page.getWidth()));
            
            // 确保尺寸有效
            if (pageW <= 0) pageW = 720;
            if (pageH <= 0) pageH = 1000;
            
            Log.d(TAG, "渲染尺寸: " + pageW + "x" + pageH);
            
            // 创建Bitmap并渲染
            Bitmap bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(0xFFFFFFFF); // 填充白色背景
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();

            Log.d(TAG, "第 " + position + " 页渲染完成");
            
            holder.imageView.setImageBitmap(bmp);
            holder.bitmap = bmp;

            // 长按摘录 — 使用 lambda 捕获 position, 但在 onClick 中用 getAdapterPosition
            final int bindPos = position;
            holder.itemView.setOnLongClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (longClickListener != null && adapterPos != RecyclerView.NO_POSITION) {
                    longClickListener.onPageLongClick(bindPos, adapterPos);
                }
                return true;
            });
            
        } catch (Exception e) {
            Log.e(TAG, "渲染第 " + position + " 页时出错", e);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PageViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.bitmap != null && !holder.bitmap.isRecycled()) {
            holder.bitmap.recycle();
        }
        holder.bitmap = null;
        holder.imageView.setImageBitmap(null);
    }

    /** 设置长按回调, 用于摘录选中文本。 */
    public void setOnPageLongClickListener(OnPageLongClickListener listener) {
        this.longClickListener = listener;
    }

    public interface OnPageLongClickListener {
        void onPageLongClick(int pageIndex, int adapterPosition);
    }

    // ---- ViewHolder ----

    static class PageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        Bitmap bitmap;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_pdf_page);
        }
    }
}
