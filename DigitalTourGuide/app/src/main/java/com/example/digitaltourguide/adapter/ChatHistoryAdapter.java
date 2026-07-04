package com.example.digitaltourguide.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.ChatMessage;

import java.util.List;

public class ChatHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_AI = 0;
    private static final int TYPE_USER = 1;
    private List<ChatMessage> messageList;

    public void setMessages(List<ChatMessage> messages) {
        this.messageList = messages;
        notifyDataSetChanged();
    }

    public void clearMessages() {
        if (this.messageList != null) {
            this.messageList.clear();
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser() ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_ai, parent, false);
            return new AiViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messageList.get(position);

        // 入场动画
        holder.itemView.startAnimation(
                AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.fade_in_scale));

        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvUserContent.setText(msg.getContent());
        } else if (holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).tvAiContent.setText(msg.getContent());
        }
    }

    @Override
    public int getItemCount() {
        return messageList == null ? 0 : messageList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserContent;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserContent = itemView.findViewById(R.id.tv_user_content);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvAiContent;

        public AiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAiContent = itemView.findViewById(R.id.tv_ai_content);
        }
    }
}