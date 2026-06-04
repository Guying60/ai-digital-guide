package com.example.digitaltourguide.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.ChatMessage;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messages;

    public void setMessages(List<ChatMessage> messages){
        this.messages=messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatAdapter.ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat,parent,false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatAdapter.ChatViewHolder holder, int position) {
        ChatMessage msg=messages.get(position);
        holder.tvContent.setText(msg.getContent());
    }

    @Override
    public int getItemCount() {
        return messages==null ? 0:messages.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder{
            TextView tvContent;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent=itemView.findViewById(R.id.tv_content);
        }
    }
}
