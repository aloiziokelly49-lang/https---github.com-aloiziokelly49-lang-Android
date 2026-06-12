package com.cloudink.app.binding;

import android.graphics.Bitmap;

import androidx.databinding.BindingAdapter;

import com.cloudink.app.rendering.PreviewCanvas;

/**
 * DataBinding 自定义适配器集合。
 * 将 ViewModel 中的 LiveData/Bitmap 映射到自定义 View 属性。
 */
public final class BindingAdapters {

    private BindingAdapters() {}

    /**
     * 将 Bitmap 绑定到 PreviewCanvas。
     * 在 XML 中使用: {@code app:renderedBitmap="@{viewModel.renderedBitmap}"}
     */
    @BindingAdapter("renderedBitmap")
    public static void setRenderedBitmap(PreviewCanvas view, Bitmap bitmap) {
        view.setBitmap(bitmap);
    }
}
