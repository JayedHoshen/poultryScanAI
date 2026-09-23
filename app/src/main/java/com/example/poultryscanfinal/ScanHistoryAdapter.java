package com.example.poultryscanfinal;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.HistoryViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ScanHistory record);
    }

    private final List<ScanHistory> items;
    private OnItemClickListener clickListener;
    private OnItemClickListener longClickListener;

    public ScanHistoryAdapter(List<ScanHistory> items) {
        this.items = new ArrayList<>(items);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemClickListener listener) {
        this.longClickListener = listener;
    }

    public void replaceAll(List<ScanHistory> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        holder.bind(items.get(position), clickListener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivThumb;
        private final TextView tvDisease;
        private final TextView tvConfidence;
        private final TextView tvDate;
        private final TextView tvTime;

        /** Guards against a recycled row showing the previous row's thumbnail. */
        private String boundImagePath;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.ivHistoryThumb);
            tvDisease = itemView.findViewById(R.id.tvHistoryDisease);
            tvConfidence = itemView.findViewById(R.id.tvHistoryConfidence);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvTime = itemView.findViewById(R.id.tvHistoryTime);
        }

        void bind(ScanHistory record, OnItemClickListener clickListener,
                  OnItemClickListener longClickListener) {

            // Below the threshold the app never claims a disease, in history either.
            boolean uncertain = record.confidence < ResultActivity.CONFIDENCE_THRESHOLD
                    || DiseaseInfo.forLabel(record.diseaseKey) == null;

            String title = uncertain
                    ? itemView.getContext().getString(R.string.uncertain_result)
                    : DiseaseInfo.displayNameFor(record.diseaseKey);

            tvDisease.setText(title);
            tvConfidence.setText(String.format(Locale.US, "Confidence: %.1f%%",
                    record.confidence * 100f));
            tvDate.setText(TimeUtils.formatDate(record.timestamp));
            tvTime.setText(TimeUtils.formatTime(record.timestamp));

            loadThumbnail(record.imagePath);

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(record);
                }
            });
            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemClick(record);
                    return true;
                }
                return false;
            });
        }

        /** A deleted or missing file simply leaves the placeholder in place. */
        private void loadThumbnail(String path) {
            boundImagePath = path;
            ivThumb.setImageDrawable(null);

            if (path == null || path.isEmpty()) {
                return;
            }
            AppExecutors.diskIO().execute(() -> {
                final Bitmap bitmap = ImageUtils.decodeStoredScanImage(path, 320);
                AppExecutors.mainThread().post(() -> {
                    if (bitmap != null && path.equals(boundImagePath)) {
                        ivThumb.setImageBitmap(bitmap);
                    }
                });
            });
        }
    }
}
