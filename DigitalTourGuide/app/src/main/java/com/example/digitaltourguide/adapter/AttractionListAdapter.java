package com.example.digitaltourguide.adapter;

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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
            holder.tvRating.setVisibility(View.VISIBLE);
            holder.tvReviewCount.setVisibility(View.VISIBLE);
            holder.tvRating.setText(String.format("%.1f分", item.getRating()));
            holder.tvReviewCount.setText(String.format("%d 条评论", item.getReviewCount()));
            holder.layoutRatingInfo.setVisibility(View.VISIBLE);
        } else if (item.getRating() != null) {
            holder.tvRating.setVisibility(View.VISIBLE);
            holder.tvReviewCount.setVisibility(View.GONE);
            holder.tvRating.setText(String.format("%.1f分", item.getRating()));
            holder.layoutRatingInfo.setVisibility(View.VISIBLE);
        } else if (item.getReviewCount() != null && item.getReviewCount() > 0) {
            holder.tvRating.setVisibility(View.GONE);
            holder.tvReviewCount.setVisibility(View.VISIBLE);
            holder.tvReviewCount.setText(String.format("%d 条评论", item.getReviewCount()));
            holder.layoutRatingInfo.setVisibility(View.VISIBLE);
        } else {
            holder.layoutRatingInfo.setVisibility(View.GONE);
        }

        // 更新时间
        String updateTimeLabel = formatUpdateTime(item.getUpdateTime());
        if (updateTimeLabel != null) {
            holder.tvUpdateTime.setText(
                    holder.itemView.getContext().getString(R.string.pm_update_time_prefix) + updateTimeLabel);
            holder.tvUpdateTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvUpdateTime.setVisibility(View.GONE);
        }

        // 开放时间
        if (item.getOpenHours() != null && !item.getOpenHours().isEmpty()) {
            holder.tvOpenHours.setText("开放时间：" + item.getOpenHours());
            holder.tvOpenHours.setVisibility(View.VISIBLE);
        } else {
            holder.tvOpenHours.setVisibility(View.GONE);
        }

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
            holder.layoutNavZone.setOnClickListener(v -> toggleSelection(position));
            holder.layoutActionButtons.setVisibility(View.GONE);
            holder.dividerActions.setVisibility(View.GONE);
            holder.layoutAnalyticsCue.setVisibility(View.GONE);
        } else {
            holder.checkBox.setVisibility(View.GONE);
            holder.layoutActionButtons.setVisibility(View.VISIBLE);
            holder.dividerActions.setVisibility(View.VISIBLE);
            holder.layoutAnalyticsCue.setVisibility(View.VISIBLE);

            // 导航区 → 数据分析；编辑/删除独立，互不抢点击
            holder.layoutNavZone.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
            holder.tvEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEditClick(item);
            });
            holder.tvDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(item);
            });
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private static String formatUpdateTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat parseFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = parseFmt.parse(raw);
            if (date == null) {
                return null;
            }
            return new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(date);
        } catch (ParseException e) {
            return raw.length() >= 10 ? raw.substring(0, 10) : raw;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEdit, tvDelete, tvRating, tvReviewCount, tvOpenHours, tvUpdateTime, tvViewAnalytics;
        ImageView ivCover;
        CheckBox checkBox;
        LinearLayout layoutRatingInfo;
        View layoutNavZone, layoutActionButtons, dividerActions, layoutAnalyticsCue;

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
            tvUpdateTime = itemView.findViewById(R.id.tv_update_time);
            layoutRatingInfo = itemView.findViewById(R.id.layout_rating_info);
            layoutNavZone = itemView.findViewById(R.id.layout_nav_zone);
            layoutActionButtons = itemView.findViewById(R.id.layout_action_buttons);
            dividerActions = itemView.findViewById(R.id.divider_actions);
            layoutAnalyticsCue = itemView.findViewById(R.id.layout_analytics_cue);
            tvViewAnalytics = itemView.findViewById(R.id.tv_view_analytics);
        }
    }
}
