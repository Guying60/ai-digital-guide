package com.example.digitaltourguide.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.admin.AddAttractionRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.util.SparseBooleanArray;

public class AttractionListAdapter extends RecyclerView.Adapter<AttractionListAdapter.ViewHolder>{
    //管理员景点列表
    private List<AddAttractionRequest> data;
    private OnItemClickListener listener;
    private boolean isMultiSelectMode = false;          // 是否多选模式
    private Set<String> selectedIds = new HashSet<>();
    public interface OnItemClickListener {
        void onItemClick(AddAttractionRequest item); //点击卡片
        void onEditClick(AddAttractionRequest item);
        void onDeleteClick(AddAttractionRequest item);
        void onItemSelected(AddAttractionRequest item, boolean isSelected); // 多选模式
    }

    public AttractionListAdapter(List<AddAttractionRequest> data, OnItemClickListener listener) {
        this.data = data;
        this.listener = listener;
    }

    // 进入多选模式
    public void setMultiSelectMode(boolean enabled) {
        if (this.isMultiSelectMode == enabled) return;
        this.isMultiSelectMode = enabled;
        if (!enabled) {
            selectedIds.clear(); // 退出时清空
        }
        notifyDataSetChanged();
    }

    public List<String> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    private void toggleSelection(int position) {
        AddAttractionRequest item = data.get(position);
        String id = item.getId();
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (listener != null) {
            listener.onItemSelected(item, selectedIds.contains(id));
        }
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attraction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AddAttractionRequest item = data.get(position);
        holder.tvName.setText(item.getAttractionName());

        // 评分 & 评论数（控制整行显隐）
        if (item.getRating() != null && item.getReviewCount() != null) {
            holder.tvRating.setText(String.format("%.1f分", item.getRating()));
            holder.tvReviewCount.setText(String.format("%d 条评论", item.getReviewCount()));
            holder.layoutRatingInfo.setVisibility(View.VISIBLE);
        } else if (item.getRating() != null) {
            holder.tvRating.setText(String.format("%.1f分", item.getRating()));
            holder.tvReviewCount.setVisibility(View.GONE);
            holder.layoutRatingInfo.setVisibility(View.VISIBLE);
        } else if (item.getReviewCount() != null && item.getReviewCount() > 0) {
            holder.tvRating.setVisibility(View.GONE);
            holder.tvReviewCount.setText(String.format("%d 条评论", item.getReviewCount()));
            holder.layoutRatingInfo.setVisibility(View.VISIBLE);
        } else {
            holder.layoutRatingInfo.setVisibility(View.GONE);
        }

        // 开放时间
        if (item.getOpenHours() != null && !item.getOpenHours().isEmpty()) {
            holder.tvOpenHours.setText("开放时间：" + item.getOpenHours());
            holder.tvOpenHours.setVisibility(View.VISIBLE);
        } else {
            holder.tvOpenHours.setVisibility(View.GONE);
        }

        //整个卡片点击
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
        holder.tvEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditClick(item);
        });
        holder.tvDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(item);
        });
        // 加载封面
        if (item.getCoverUrl() != null && !item.getCoverUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(item.getCoverUrl())
                    .placeholder(R.drawable.ic_pdf)
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_pdf);
        }

        // 处理多选模式下的复选框显示
        if (isMultiSelectMode) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(selectedIds.contains(item.getId()));
            holder.checkBox.setOnClickListener(v -> toggleSelection(position));
            holder.itemView.setOnClickListener(null);
            // 编辑和删除按钮在多选模式下隐藏或禁用
            holder.tvEdit.setVisibility(View.GONE);
            holder.tvDelete.setVisibility(View.GONE);
        } else {
            holder.checkBox.setVisibility(View.GONE);
            // 恢复正常的点击事件
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
            holder.tvEdit.setVisibility(View.VISIBLE);
            holder.tvDelete.setVisibility(View.VISIBLE);
            holder.tvEdit.setOnClickListener(v -> listener.onEditClick(item));
            holder.tvDelete.setOnClickListener(v -> listener.onDeleteClick(item));
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType, tvEdit, tvDelete, tvRating, tvReviewCount, tvOpenHours;
        ImageView ivCover;
        CheckBox checkBox;
        LinearLayout layoutRatingInfo;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvEdit = itemView.findViewById(R.id.tv_edit);
            tvDelete = itemView.findViewById(R.id.tv_delete);
            checkBox = itemView.findViewById(R.id.checkbox_select);
            tvRating = itemView.findViewById(R.id.tv_rating);
            tvReviewCount = itemView.findViewById(R.id.tv_review_count);
            tvOpenHours = itemView.findViewById(R.id.tv_open_hours);
            layoutRatingInfo = itemView.findViewById(R.id.layout_rating_info);
        }
    }

    private String getTypeName(int type) {
        String[] types = {"主题乐园", "博物馆与展馆", "自然公园", "风景名胜与休闲度假",
                "历史文化", "古镇水乡", "动植物园与水族馆", "现代地标"};
        return type >= 0 && type < types.length ? types[type] : "未知";
    }
}
