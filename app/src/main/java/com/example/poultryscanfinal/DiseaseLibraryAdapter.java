package com.example.poultryscanfinal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DiseaseLibraryAdapter
        extends RecyclerView.Adapter<DiseaseLibraryAdapter.DiseaseViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DiseaseInfo info);
    }

    private final List<DiseaseInfo> items;
    private OnItemClickListener clickListener;

    public DiseaseLibraryAdapter(List<DiseaseInfo> items) {
        this.items = new ArrayList<>(items);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public DiseaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_disease, parent, false);
        return new DiseaseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DiseaseViewHolder holder, int position) {
        holder.bind(items.get(position), clickListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class DiseaseViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvSummary;

        DiseaseViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDiseaseCardName);
            tvSummary = itemView.findViewById(R.id.tvDiseaseCardSummary);
        }

        void bind(DiseaseInfo info, OnItemClickListener listener) {
            tvName.setText(info.displayName);
            tvSummary.setText(info.shortDescription);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(info);
                }
            });
        }
    }
}
