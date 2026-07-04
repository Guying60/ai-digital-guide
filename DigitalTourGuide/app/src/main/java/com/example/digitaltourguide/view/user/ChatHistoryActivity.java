package com.example.digitaltourguide.view.user;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.ChatHistoryAdapter;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.ChatHistoryItem;
import com.example.digitaltourguide.model.user.ChatMessage;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ChatHistoryActivity";
    private RecyclerView rvChatHistory;
    private ChatHistoryAdapter adapter;
    private String conversationId;
    private View layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear) {
                clearConversation();
                return true;
            }
            return false;
        });

        layoutEmpty = findViewById(R.id.layout_empty);

        conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null || conversationId.isEmpty()) {
            Toast.makeText(this, "缺少对话ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        rvChatHistory = findViewById(R.id.rv_chat_history);
        rvChatHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatHistoryAdapter();
        rvChatHistory.setAdapter(adapter);

        loadChatHistory();
    }

    private void clearConversation() {
        if (adapter != null) {
            adapter.clearMessages();
        }
        layoutEmpty.setVisibility(View.VISIBLE);
        rvChatHistory.setVisibility(View.GONE);
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvChatHistory.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void loadChatHistory() {
        String token = SpUtils.getUserToken(this);
        Log.d(TAG, "获取到的token: " + token);

        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "未登录，请重新登录", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;
        }
        Log.d(TAG, "token: " + token);

        ApiService apiService = RetrofitClient.getApiService();
        apiService.getChatHistory(conversationId).enqueue(new Callback<BaseResponse<List<ChatHistoryItem>>>() {
            @Override
            public void onResponse(Call<BaseResponse<List<ChatHistoryItem>>> call, Response<BaseResponse<List<ChatHistoryItem>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "conversationId:" + conversationId);
                    Log.d(TAG, response.body().toString());
                    BaseResponse<List<ChatHistoryItem>> baseResp = response.body();
                    Log.d(TAG, "code = " + baseResp.getCode());
                    Log.d(TAG, "msg = " + baseResp.getMsg());
                    Log.d(TAG, "data = " + baseResp.getData());
                    if (baseResp.getData() != null) {
                        Log.d(TAG, "data size = " + baseResp.getData().size());
                    } else {
                        Log.e(TAG, "data 为 null");
                    }
                    if (baseResp.getCode() == 1) {
                        List<ChatHistoryItem> items = baseResp.getData();
                        if (items == null || items.isEmpty()) {
                            Toast.makeText(ChatHistoryActivity.this, "暂无聊天记录", Toast.LENGTH_SHORT).show();
                            updateEmptyState();
                        } else {
                            List<ChatMessage> messages = new ArrayList<>();
                            for (ChatHistoryItem item : items) {
                                boolean isUser = "user".equals(item.getRole());
                                messages.add(new ChatMessage(item.getContent(), isUser));
                            }
                            adapter.setMessages(messages);
                            updateEmptyState();
                        }
                    } else {
                        Toast.makeText(ChatHistoryActivity.this, "接口错误：" + baseResp.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "响应失败，code=" + response.code());
                    Toast.makeText(ChatHistoryActivity.this, "HTTP错误：" + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<List<ChatHistoryItem>>> call, Throwable t) {
                Toast.makeText(ChatHistoryActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }
}