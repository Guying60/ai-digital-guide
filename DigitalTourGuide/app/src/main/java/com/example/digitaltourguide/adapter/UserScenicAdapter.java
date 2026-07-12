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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

        // ── 对话统计（后端 messageCount / lastChatTime） ──
        Integer messageCount = spot.getMessageCount();
        if (messageCount != null && messageCount > 0) {
            holder.tvMessageCount.setText(messageCount + " 条对话");
            holder.tvMessageCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvMessageCount.setVisibility(View.GONE);
        }

        String lastTimeLabel = formatLastChatTime(spot.getLastChatTime());
        if (lastTimeLabel != null) {
            holder.tvLastTime.setText("· " + lastTimeLabel);
            holder.tvLastTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvLastTime.setVisibility(View.GONE);
        }


        // 根据是否已评价（或已结束）决定显示哪个按钮布局
        if (isRated) {
            // 已评价：只保留导航区，隐藏操作区
            holder.layoutActionZone.setVisibility(View.GONE);
        } else if (spot.isEnded()) {
            holder.layoutActionZone.setVisibility(View.VISIBLE);
            holder.layoutDualButtons.setVisibility(View.GONE);
            holder.layoutSingleButton.setVisibility(View.VISIBLE);
            holder.btnRate.setText(R.string.history_rate_attraction);
            holder.btnRate.setEnabled(true);
            holder.btnRate.setAlpha(1f);
        } else {
            holder.layoutActionZone.setVisibility(View.VISIBLE);
            holder.layoutDualButtons.setVisibility(View.VISIBLE);
            holder.layoutSingleButton.setVisibility(View.GONE);
        }

        // 导航区点击 → 查看聊天记录（与底部操作按钮分离）
        View.OnClickListener openHistory = v -> {
            if (listener != null) {
                listener.onItemClick(spot);
            } else {
                Intent intent = new Intent(context, ChatHistoryActivity.class);
                intent.putExtra("conversationId", spot.getConversationId());
                context.startActivity(intent);
            }
        };
        holder.layoutNavZone.setOnClickListener(openHistory);

        // 长按删除（挂在导航区，避免与操作按钮冲突）
        holder.layoutNavZone.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(spot, holder.getBindingAdapterPosition());
            }
            return true;
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(spot, holder.getBindingAdapterPosition());
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
            notifyItemChanged(holder.getBindingAdapterPosition());
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

    /**
     * 格式化上次对话时间：今天显示 HH:mm，昨天显示「昨天 HH:mm」，更早显示 MM-dd。
     */
    private static String formatLastChatTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat parseFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = parseFmt.parse(raw);
            if (date == null) {
                return null;
            }
            Calendar target = Calendar.getInstance();
            target.setTime(date);
            Calendar today = Calendar.getInstance();
            clearTime(today);
            Calendar targetDay = (Calendar) target.clone();
            clearTime(targetDay);

            long dayDiff = TimeUnit.MILLISECONDS.toDays(today.getTimeInMillis() - targetDay.getTimeInMillis());
            String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
            if (dayDiff == 0) {
                return hm;
            }
            if (dayDiff == 1) {
                return "昨天 " + hm;
            }
            return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(date);
        } catch (ParseException e) {
            return raw.length() >= 16 ? raw.substring(11, 16) : raw;
        }
    }

    private static void clearTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    public static class ScenicHolder extends RecyclerView.ViewHolder{
        TextView tvTitle,tvStopChat, tvContinueChat,btnRate;
        TextView tvStatus, tvCategory, tvMessageCount, tvLastTime;
        ImageView ivCover;
        View layoutDualButtons, layoutSingleButton, layoutNavZone, layoutActionZone;

        public ScenicHolder(@NonNull View itemView) {

            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvStopChat = itemView.findViewById(R.id.btn_end);
            tvContinueChat = itemView.findViewById(R.id.btn_continue);
            btnRate = itemView.findViewById(R.id.btn_rate);
            layoutDualButtons = itemView.findViewById(R.id.layout_dual_buttons);
            layoutSingleButton = itemView.findViewById(R.id.layout_single_button);
            layoutNavZone = itemView.findViewById(R.id.layout_nav_zone);
            layoutActionZone = itemView.findViewById(R.id.layout_action_zone);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvMessageCount = itemView.findViewById(R.id.tv_message_count);
            tvLastTime = itemView.findViewById(R.id.tv_last_time);
        }

    }
}
