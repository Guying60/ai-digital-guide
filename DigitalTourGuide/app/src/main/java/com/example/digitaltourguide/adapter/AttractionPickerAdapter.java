package com.example.digitaltourguide.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.ScenicSpot;

import java.util.List;

public class AttractionPickerAdapter extends RecyclerView.Adapter<AttractionPickerAdapter.ViewHolder> {
    private final Context context;
    private final List<ScenicSpot> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ScenicSpot spot);
    }

    public AttractionPickerAdapter(Context context, List<ScenicSpot> list) {
        this.context = context;
        this.list = list;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attraction_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScenicSpot spot = list.get(position);
        holder.tvTitle.setText(spot.getTitle());
        String url = spot.getCoverUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.ic_pdf)
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_pdf);
        }

        // 评分星级（PointManagerActivity 风格）
        double rating = spot.getRating() != null ? spot.getRating() : 0.0;
        int reviewCount = spot.getReviewCount() != null ? spot.getReviewCount() : 0;
        holder.tvRating.setText(String.format("%.1f分", rating));
        holder.tvReviewCount.setText(String.format("%d 条评论", reviewCount));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(spot);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvRating, tvReviewCount;
        ImageView ivCover;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvRating = itemView.findViewById(R.id.tv_rating);
            tvReviewCount = itemView.findViewById(R.id.tv_review_count);
        }
    }
}