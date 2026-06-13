package com.cloudink.app.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudink.app.R;
import com.cloudink.app.data.local.entity.HandwriteRecord;
import com.cloudink.app.databinding.FragmentHistoryBinding;
import com.cloudink.app.ui.editor.HandwriteEditorActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史档案室 —— Room 手写记录列表, 点击可回溯编辑。
 *
 * <h3>恢复参数</h3>
 * 点击条目时, 将 {@link HandwriteRecord} 中保存的字距/行距/抖动/纸张/笔触/内容
 * 作为 Intent extras 传给 {@link HandwriteEditorActivity}, 实现"档案可回溯编辑"。
 */
public class HistoryFragment extends Fragment {

    private FragmentHistoryBinding binding;
    private HistoryViewModel viewModel;
    private HistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);

        adapter = new HistoryAdapter(record -> {
            // 点击条目 → 打开编辑器并恢复排版参数
            Intent intent = new Intent(requireContext(), HandwriteEditorActivity.class);
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_TITLE, record.getTitle());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_CONTENT, record.getContent());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_CHAR_SP, record.getCharSpacing());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_LINE_SP, record.getLineSpacing());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_JITTER, record.getJitterThreshold());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_PAPER, record.getPaperIndex());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_PEN, record.getPenType());
            intent.putExtra(HandwriteEditorActivity.EXTRA_RESTORE_FONT, record.getFontPath());
            startActivity(intent);
        });

        binding.rvHistory.setAdapter(adapter);

        viewModel.getAllRecords().observe(getViewLifecycleOwner(), records -> {
            adapter.submitList(records);
            binding.tvEmpty.setVisibility(
                (records == null || records.isEmpty()) ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ================================================================
    // Adapter
    // ================================================================

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        private final OnRecordClickListener listener;
        private List<HandwriteRecord> items = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

        interface OnRecordClickListener {
            void onClick(HandwriteRecord record);
        }

        HistoryAdapter(OnRecordClickListener l) { this.listener = l; }

        void submitList(List<HandwriteRecord> list) {
            items = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_record, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            HandwriteRecord r = items.get(pos);
            h.title.setText(r.getTitle());
            h.date.setText(sdf.format(new Date(r.getUpdatedAt())));
            h.preview.setText(r.getContent() != null ? r.getContent() : "");
            h.folder.setText("📁 " + r.getFolderName());
            h.storagePath.setText(r.getStoragePath());
            h.pen.setText(penLabel(r.getPenType()));
            h.itemView.setOnClickListener(v -> listener.onClick(r));
        }

        @Override
        public int getItemCount() { return items.size(); }

        private static String penLabel(String pen) {
            switch (pen != null ? pen : "") {
                case "ballpoint": return "圆珠笔";
                case "marker":    return "马克笔";
                default:          return "钢笔";
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView title, date, preview, folder, storagePath, pen, tags;
            VH(@NonNull View v) {
                super(v);
                title      = v.findViewById(R.id.tv_title);
                date       = v.findViewById(R.id.tv_date);
                preview    = v.findViewById(R.id.tv_preview);
                folder     = v.findViewById(R.id.tv_folder);
                storagePath = v.findViewById(R.id.tv_storage_path);
                pen        = v.findViewById(R.id.tv_pen);
                tags       = v.findViewById(R.id.tv_tags);
            }
        }
    }
}
