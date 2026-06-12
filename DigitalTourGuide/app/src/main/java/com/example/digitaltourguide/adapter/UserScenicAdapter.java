package com.example.digitaltourguide.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.ScenicSpot;
import com.example.digitaltourguide.view.user.ChatHistoryActivity;
import com.example.digitaltourguide.utils.SpUtils;

import java.util.List;

public class UserScenicAdapter extends RecyclerView.Adapter<UserScenicAdapter.ScenicHolder> {
    //用户主页列表
    private final Context context;
    private final List<ScenicSpot> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener{
        void onItemClick(ScenicSpot spot);
        void onItemLongClick(ScenicSpot spot, int position);   // 新增长按
        void onStopChatClick(ScenicSpot spot);
        void onContinueChatClick(ScenicSpot spot);
        void onRateClick(ScenicSpot spot);
    }


    public UserScenicAdapter(Context context, List<ScenicSpot> list){
        this.context=context;
        this.list=list;
    }

    public void setOnItemClickListener(OnItemClickListener listener){
        this.listener=listener;
    }

    public void clearData() {
        synchronized (list) {
        int size = this.list.size();
        if (size > 0) {
            this.list.clear();
            notifyItemRangeRemoved(0, size);
        }
        }
    }
    public void addData(List<ScenicSpot> newList) {
        if (newList != null && !newList.isEmpty()) {
            synchronized (list) {
                int start = this.list.size();
                this.list.addAll(newList);
                notifyItemRangeInserted(start, newList.size());
            }
        }
    }


    @NonNull
    @Override
    public ScenicHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scenic_dialog,parent,false);
        return new ScenicHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScenicHolder holder, int position) {
        ScenicSpot spot=list.get(position);
        holder.tvTitle.setText(spot.getTitle());
        // 加载图片（使用 Glide RoundedCorners 实现圆角，兼容 API 24+）
        String url = spot.getCoverUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.ic_pdf)
                    .transform(new RoundedCorners(12))
                    .into(holder.ivCover);
        }else{
            holder.ivCover.setImageResource(R.drawable.ic_pdf);
        }

        // ── 状态徽章 ──
        // 与按钮可见性共用 isRated / isEnded 判断，不改变原有逻辑
        boolean isRated = SpUtils.isRated(context, spot.getConversationId());
        String statusText;
        String statusBgColor;
        String statusTextColor;
        if (isRated) {
            statusText = "已评价";
            statusBgColor = "#E6FFE6";
            statusTextColor = "#008000";
        } else if (spot.isEnded()) {
            statusText = "已结束";
            statusBgColor = "#FFF4E6";
            statusTextColor = "#CC6600";
        } else {
            statusText = "进行中";
            statusBgColor = "#E6F0FF";
            statusTextColor = "#0066CC";
        }
        holder.tvStatus.setText(statusText);
        holder.tvStatus.setVisibility(View.VISIBLE);
        GradientDrawable statusBg = (GradientDrawable) holder.tvStatus.getBackground().mutate();
        statusBg.setColor(Color.parseColor(statusBgColor));
        holder.tvStatus.setTextColor(Color.parseColor(statusTextColor));

        // ── 分类标签（暂无数据，默认隐藏） ──
        holder.tvCategory.setVisibility(View.GONE);

        // ── 上次对话时间（暂无数据，默认隐藏） ──
        holder.tvLastTime.setVisibility(View.GONE);

        // 根据是否已评价（或已结束）决定显示哪个按钮布局
        if (isRated || spot.isEnded()) {
            holder.layoutDualButtons.setVisibility(View.GONE);
            holder.layoutSingleButton.setVisibility(View.VISIBLE);
            // 如果已经评价过，可以禁用评价按钮或改变文字
            if (isRated) {
                holder.btnRate.setText("已评价");
                holder.btnRate.setEnabled(false);
                holder.btnRate.setAlpha(0.5f);
            } else {
                holder.btnRate.setText("评价景点");
                holder.btnRate.setEnabled(true);
                holder.btnRate.setAlpha(1f);
            }
        } else {
            holder.layoutDualButtons.setVisibility(View.VISIBLE);
            holder.layoutSingleButton.setVisibility(View.GONE);
        }

        // 整张卡片点击 → 查看聊天记录
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(spot);
            } else {
                // 默认行为：跳转到聊天历史页面
                Intent intent = new Intent(context, ChatHistoryActivity.class);
                intent.putExtra("conversationId", spot.getConversationId());
                context.startActivity(intent);
            }
        });

        // 长按删除
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(spot, holder.getAdapterPosition());
            }
            return true;
        });

        // 结束对话按钮
        holder.tvStopChat.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStopChatClick(spot);
            }
            // 更新本地状态并刷新UI
            spot.setEnded(true);
            notifyItemChanged(holder.getAdapterPosition());
        });

        // 继续对话按钮
        holder.tvContinueChat.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContinueChatClick(spot);
            }
        });

        // 评价景点按钮
        holder.btnRate.setOnClickListener(v -> {
            // 如果已经评价过，不允许重复评价
            if (SpUtils.isRated(context, spot.getConversationId())) {
                if (listener != null) {
                    // 可以触发一个提示，或者交给外部处理
                    listener.onRateClick(spot); // 外部可弹出“您已评价过”
                }
                return;
            }
            if (listener != null) {
                listener.onRateClick(spot);
            }
        });


    }

    @Override
    public int getItemCount() {
        return list== null ? 0 : list.size();
    }

    public static class ScenicHolder extends RecyclerView.ViewHolder{
        TextView tvTitle,tvStopChat, tvContinueChat,btnRate;
        TextView tvStatus, tvCategory, tvLastTime;
        ImageView ivCover;
        View layoutDualButtons,layoutSingleButton;

        public ScenicHolder(@NonNull View itemView) {

            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvStopChat = itemView.findViewById(R.id.btn_end);
            tvContinueChat = itemView.findViewById(R.id.btn_continue);
            btnRate = itemView.findViewById(R.id.btn_rate);
            layoutDualButtons = itemView.findViewById(R.id.layout_dual_buttons);
            layoutSingleButton = itemView.findViewById(R.id.layout_single_button);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvLastTime = itemView.findViewById(R.id.tv_last_time);
        }

    }
}
