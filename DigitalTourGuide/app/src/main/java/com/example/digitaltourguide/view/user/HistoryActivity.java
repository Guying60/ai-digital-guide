package com.example.digitaltourguide.view.user;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.UserScenicAdapter;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.DeleteResponse;
import com.example.digitaltourguide.model.user.EvaluateRequest;
import com.example.digitaltourguide.model.user.HistoryResponse;
import com.example.digitaltourguide.model.user.ScenicSpot;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {
    private static final String TAG="HistoryActivity";
    private static final int REQ_LOCATION = 5001;
    /** AttractionPickerDialog 启动 ChatActivity 用的请求码，用于 onActivityResult 刷新 */
    public static final int REQUEST_CHAT = 200;
    private EditText etSearch;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvScenic;
    private UserScenicAdapter adapter;
    private List<ScenicSpot> dataList = new ArrayList<>();
    // 分页
    private String lastId = null;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private final int pageSize = 10;
    // 筛选参数
    private String currentKeyWord = null;   // 没输入时为 null
    private String currentCity = null;      // 没选择时为 null
    /** desc=最新在前（默认），asc=最早在前 */
    private String sortOrder = "desc";
    private TextView tvSortTime;
    /** 列表加载代数：切换排序/筛选时递增，丢弃过期回调 */
    private int loadSeq = 0;
    // 城市标签（复用原来的类型标签 View）
    private final TextView[] cityTagViews = new TextView[8];
    private String[] cityNames = new String[0];
    private ApiService apiService;
    private String token;
    private int currentClickPosition = -1;
   private List<ScenicSpot> list;
   private ImageView ivAdd;
   private LinearLayout tvHistory,tvMine;
    private UserScenicAdapter.OnItemClickListener listener;
    private boolean needRefresh = false;      // 从 ChatActivity 返回时需要刷新列表
    private View layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initView();
        initNetwork();
        setupRecyclerView();
        setupSearchButton();
        setupTags();
        loadFirstPage(); // 进入页面立即请求

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needRefresh) {
            needRefresh = false;
            resetAndLoad();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHAT) {
            // 从 ChatActivity 返回后强制刷新列表，无论 resultCode 是什么
            needRefresh = true;
            resetAndLoad();
        }
    }
    private void initNetwork() {
        token = SpUtils.getUserToken(this);
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;  // 根据你拦截器的情况调整
        }
        apiService = RetrofitClient.getApiService();
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 1);
        rvScenic.setLayoutManager(layoutManager);
        rvScenic.setHasFixedSize(true);
        rvScenic.setItemViewCacheSize(10);
        rvScenic.setDrawingCacheEnabled(true);
        rvScenic.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        adapter = new UserScenicAdapter(this, dataList);

        adapter.setOnItemClickListener(new UserScenicAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ScenicSpot spot) {
                // 点击卡片：查看聊天记录
                String conversationId= spot.getConversationId();
                Intent intent=new Intent(HistoryActivity.this, ChatHistoryActivity.class);
                intent.putExtra("conversationId",conversationId);
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(ScenicSpot spot, int position) {
                // 弹出自定义删除确认弹窗
                Dialog dialog = new Dialog(HistoryActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                View view = getLayoutInflater().inflate(R.layout.dialog_delete_confirm, null);
                dialog.setContentView(view);

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setLayout(
                            (int) (getResources().getDisplayMetrics().widthPixels * 0.82),
                            WindowManager.LayoutParams.WRAP_CONTENT
                    );
                    window.setGravity(Gravity.CENTER);
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                view.findViewById(R.id.btn_cancel_delete).setOnClickListener(v -> dialog.dismiss());
                view.findViewById(R.id.btn_confirm_delete).setOnClickListener(v -> {
                    deleteRecord(spot, position);
                    dialog.dismiss();
                });

                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
            }

            @Override
            public void onStopChatClick(ScenicSpot spot) {
                showConversationEndedDialog(spot);
            }

            @Override
            public void onContinueChatClick(ScenicSpot spot) {
                Intent intent = new Intent(HistoryActivity.this, ChatActivity.class);
                intent.putExtra("attractionId", spot.getId());           // 景点 ID
                intent.putExtra("conversationId", spot.getConversationId()); // 会话 ID（用于恢复对话）
                startActivity(intent);
            }

            @Override
            public void onRateClick(ScenicSpot spot) {
                // 点击评价景点：弹出评分弹窗
                Log.d(TAG,"点击了评价景点");
                showRatingDialog(spot);
            }
        });

        rvScenic.setAdapter(adapter);

        rvScenic.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (!isLoading && hasMore && layoutManager != null) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
                    if ((visibleItemCount + firstVisiblePosition) >= totalItemCount - 3) {
                        loadNextPage();
                    }
                }
            }
        });
    }

    private void showConversationEndedDialog(ScenicSpot spot) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = getLayoutInflater().inflate(R.layout.dialog_conversation_ended, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.82),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // 去评价 → 关闭当前弹窗，弹出评分弹窗
        view.findViewById(R.id.btn_go_rate).setOnClickListener(v -> {
            dialog.dismiss();
            showRatingDialog(spot);
        });

        // 继续对话 → 关闭弹窗，跳转到 ChatActivity 继续对话
        view.findViewById(R.id.btn_continue_chat).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(HistoryActivity.this, ChatActivity.class);
            intent.putExtra("attractionId", spot.getId());
            intent.putExtra("conversationId", spot.getConversationId());
            startActivity(intent);
        });

        // 返回记录 → 二次确认后关闭弹窗，通知后端结束对话并刷新
        view.findViewById(R.id.btn_back_history).setOnClickListener(v -> {
            new AlertDialog.Builder(HistoryActivity.this)
                    .setTitle("结束对话")
                    .setMessage("确认结束当前对话？")
                    .setPositiveButton("确认结束", (confirmDialog, which) -> {
                        confirmDialog.dismiss();
                        dialog.dismiss();
                        // 调用后端结束对话接口
                        apiService.endTourHistory(spot.getConversationId()).enqueue(new Callback<BaseResponse<Void>>() {
                            @Override
                            public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                                Log.d(TAG, "结束对话接口调用成功");
                            }
                            @Override
                            public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                                Log.w(TAG, "结束对话接口调用失败: " + t.getMessage());
                            }
                        });
                        // 本地立刻更新 UI（服务端状态以接口返回为准）
                        spot.setTourStatus(1); // ENDED
                        spot.setEnded(true);
                        int position = dataList.indexOf(spot);
                        if (position != -1) {
                            adapter.notifyItemChanged(position);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showRatingDialog(ScenicSpot spot) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View view = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // ── 星星评分 ──
        final int[] selectedScore = {0};
        ImageView[] stars = new ImageView[5];
        stars[0] = view.findViewById(R.id.star_1);
        stars[1] = view.findViewById(R.id.star_2);
        stars[2] = view.findViewById(R.id.star_3);
        stars[3] = view.findViewById(R.id.star_4);
        stars[4] = view.findViewById(R.id.star_5);

        for (int i = 0; i < 5; i++) {
            final int starIndex = i;
            stars[i].setOnClickListener(v -> {
                selectedScore[0] = starIndex + 1;
                updateStars(stars, selectedScore[0]);
            });
        }

        // ── 评分标签 (点击高亮切换，同时填充到输入框) ──
        int[] tagIds = {
                R.id.tag_professional, R.id.tag_rich, R.id.tag_fun,
                R.id.tag_clear, R.id.tag_knowledgeable, R.id.tag_excellent
        };
        EditText editFeedback = view.findViewById(R.id.edit_feedback);
        for (int id : tagIds) {
            TextView tag = view.findViewById(id);
            tag.setOnClickListener(v -> {
                boolean isSelected = tag.isSelected();
                tag.setSelected(!isSelected);
                tag.setBackgroundResource(isSelected
                        ? R.drawable.bg_tag_rating
                        : R.drawable.bg_chip_filter_selected);
                tag.setTextColor(getColor(isSelected
                        ? R.color.profile_primary
                        : R.color.profile_on_primary));

                // 自动填写/移除标签文字到输入框
                String tagText = tag.getText().toString();
                String currentText = editFeedback.getText().toString();
                if (!isSelected) {
                    // 选中：追加标签文字
                    if (!currentText.isEmpty() && !currentText.endsWith("，")) {
                        currentText += "，";
                    }
                    editFeedback.setText(currentText + tagText);
                } else {
                    // 取消选中：移除标签文字
                    // 处理 "讲解专业" 或 "讲解专业，" 两种情况
                    String cleaned = currentText.replace(tagText + "，", "")
                                                .replace("，" + tagText, "")
                                                .replace(tagText, "");
                    // 清理多余逗号
                    cleaned = cleaned.replaceAll("，，", "，")
                                     .replaceAll("^，", "")
                                     .replaceAll("，$", "");
                    editFeedback.setText(cleaned);
                }
                // 光标移到末尾
                editFeedback.setSelection(editFeedback.getText().length());
            });
        }

        // ── 评论文本 + 字数统计 ──
        TextView tvCharCount = view.findViewById(R.id.tv_char_count);
        editFeedback.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvCharCount.setText(s.length() + "/200");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // ── 取消 ──
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        // ── 提交 ──
        view.findViewById(R.id.btn_submit).setOnClickListener(v -> {
            if (selectedScore[0] == 0) {
                Toast.makeText(this, "请评分", Toast.LENGTH_SHORT).show();
                return;
            }
            String feedback = editFeedback.getText().toString().trim();
            submitRating(spot, selectedScore[0], feedback);
            dialog.dismiss();
        });

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void updateStars(ImageView[] stars, int score) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setImageTintList(ColorStateList.valueOf(getColor(
                    i < score ? R.color.profile_star_yellow : R.color.profile_outline_variant
            )));
        }
    }

    private void submitRating(ScenicSpot spot, int score, String feedback) {
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        EvaluateRequest request = new EvaluateRequest(spot.getConversationId(), score, feedback);
        RetrofitClient.getApiService()
                .evaluateTourHistory("Bearer " + token,request)
                .enqueue(new Callback<BaseResponse>() {
                    @Override
                    public void onResponse(Call<BaseResponse> call, Response<BaseResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            if (response.body().getCode() == 1) {
                                // 成功
                                Toast.makeText(HistoryActivity.this, "评价成功", Toast.LENGTH_SHORT).show();
                                SpUtils.addRatedConversation(HistoryActivity.this,spot.getConversationId());
                                spot.setTourStatus(2); // RATED
                                spot.setEnded(true);
                                //刷新对应的item
                                int position=dataList.indexOf(spot);
                                if(position!=-1){
                                    adapter.notifyItemChanged(position);
                                }
                            } else {
                                String msg=response.body()!=null ? response.body().getMsg() : "评价失败";
                                Toast.makeText(HistoryActivity.this, "评价失败：" + msg, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(HistoryActivity.this, "请求失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse> call, Throwable t) {
                        Toast.makeText(HistoryActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteRecord(ScenicSpot spot, int position) {
        String recordId = spot.getId();   // 旅游历史记录的 ID，不是景点ID
        String conversationId=spot.getConversationId();

        if (recordId == null) {
            Toast.makeText(this, "记录ID缺失", Toast.LENGTH_SHORT).show();
            return;
        }
        //1.
        apiService.deleteTourHistory(recordId).enqueue(new Callback<DeleteResponse>() {
            @Override
            public void onResponse(Call<DeleteResponse> call, Response<DeleteResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().code == 1) {
                    // 从列表中移除该项
                    dataList.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateEmptyState();
                    Toast.makeText(HistoryActivity.this, "已删除", Toast.LENGTH_SHORT).show();

                    //2.删除聊天记录
                    if (conversationId != null && !conversationId.isEmpty()) {
                        apiService.deleteAiHistory(conversationId).enqueue(new Callback<DeleteResponse>() {
                            @Override
                            public void onResponse(Call<DeleteResponse> call, Response<DeleteResponse> response) {
                                if (response.isSuccessful() && response.body() != null && response.body().code != 1) {
                                    Toast.makeText(HistoryActivity.this, "聊天记录删除成功", Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override
                            public void onFailure(Call<DeleteResponse> call, Throwable t) {
                                Toast.makeText(HistoryActivity.this, "聊天记录删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    String msg = response.body() != null ? response.body().msg : "删除失败";
                    Toast.makeText(HistoryActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DeleteResponse> call, Throwable t) {
                Toast.makeText(HistoryActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
            }
        });


    }

    private void setupSearchButton(){
        View btnSearch=findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v->{
            String keyword=etSearch.getText().toString().trim();
            currentKeyWord= TextUtils.isEmpty(keyword) ? null:keyword;
            resetAndLoad();
        });

        // 键盘回车键触发搜索
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                String keyword = etSearch.getText().toString().trim();
                currentKeyWord = TextUtils.isEmpty(keyword) ? null : keyword;
                resetAndLoad();
                return true;
            }
            return false;
        });

        tvSortTime.setOnClickListener(v -> {
            boolean toAsc = !"asc".equalsIgnoreCase(sortOrder);
            sortOrder = toAsc ? "asc" : "desc";
            tvSortTime.setText(toAsc ? R.string.history_sort_asc : R.string.history_sort_desc);
            resetAndLoad();
        });
    }

    /** 根据当前数据列表是否为空，切换空状态占位图 */
    private void updateEmptyState() {
        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(dataList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void resetAndLoad() {
        loadSeq++;
        lastId = null;
        hasMore = true;
        isLoading = false;
        adapter.clearData();
        updateEmptyState();
        loadFirstPage();
    }

    private void loadFirstPage() {
        token = SpUtils.getUserToken(this);
        Log.d("HistoryActivity", "此时用户token: " + token);
        final int seq = loadSeq;
        isLoading = true;
        apiService.getTourHistory(currentKeyWord, null, currentCity, null, pageSize, sortOrder)
                .enqueue(new Callback<HistoryResponse>() {
                    @Override
                    public void onResponse(Call<HistoryResponse> call, Response<HistoryResponse> response) {
                        isLoading = false;
                        swipeRefresh.setRefreshing(false);
                        if (seq != loadSeq) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().code == 1) {
                            String bodyJson = new Gson().toJson(response.body());
                            Log.d("HistoryActivity", "响应体 JSON: " + bodyJson);
                            Log.d("HistoryActivity", "业务 code: " + response.body().code);
                            Log.d("HistoryActivity", "业务 msg: " + response.body().msg);
                            HistoryResponse.HistoryData data = response.body().data;
                            if (data != null) {
                                List<ScenicSpot> newList = data.list;
                                adapter.clearData();
                                if (newList != null && !newList.isEmpty()) {
                                    List<ScenicSpot> uniqueList = new ArrayList<>();
                                    for (ScenicSpot spot : newList) {
                                        boolean exists = false;
                                        for (ScenicSpot existing : dataList) {
                                            if (existing.getId().equals(spot.getId())) {
                                                exists = true;
                                                break;
                                            }
                                        }
                                        if (SpUtils.isRated(HistoryActivity.this, spot.getConversationId())) {
                                            spot.setEnded(true);
                                        }
                                        if (!exists) {
                                            uniqueList.add(spot);
                                        }
                                    }
                                    lastId = data.nextLastId;
                                    hasMore = data.hasMore;
                                    isLoading = false;
                                    adapter.addData(uniqueList);
                                    updateEmptyState();
                                    updateCityTags();
                                    maybeLoadMoreIfNeeded();
                                } else {
                                    isLoading = false;
                                    updateEmptyState();
                                    Toast.makeText(HistoryActivity.this, "未找到相关景点", Toast.LENGTH_SHORT).show();
                                    lastId = null;
                                    hasMore = false;
                                }
                            } else {
                                isLoading = false;
                            }
                        } else {
                            isLoading = false;
                            HistoryResponse body = response.body();
                            if (body == null) {
                                Log.e("HistoryActivity", "景点请求失败: 响应体为 null, HTTP状态码=" + response.code());
                                Toast.makeText(HistoryActivity.this, "景点请求失败", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String bodyJson = new Gson().toJson(body);
                            Log.e("HistoryActivity","景点请求失败:" + body.msg);
                            Log.e("HistoryActivity", "响应体 JSON: " + bodyJson);
                            Log.e("HistoryActivity", "业务 code: " + body.code);
                            Log.e("HistoryActivity", "业务 msg: " + body.msg);
                            Toast.makeText(HistoryActivity.this,"景点请求失败",Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<HistoryResponse> call, Throwable t) {
                        if (seq != loadSeq) {
                            return;
                        }
                        isLoading = false;
                        swipeRefresh.setRefreshing(false);
                        Log.e("HistoryActivity", "网络请求失败: " + t.getMessage(), t);
                        Toast.makeText(HistoryActivity.this,"网络错误",Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadNextPage() {
        Log.d(TAG, "loadNextPage, lastId=" + lastId + ", hasMore=" + hasMore);
        token = SpUtils.getUserToken(this);
        if (!hasMore || isLoading) return;
        final int seq = loadSeq;
        isLoading = true;
        apiService.getTourHistory(currentKeyWord, null, currentCity, lastId, pageSize, sortOrder)
                .enqueue(new Callback<HistoryResponse>() {
                    @Override
                    public void onResponse(Call<HistoryResponse> call, Response<HistoryResponse> response) {
                        if (seq != loadSeq) {
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null && response.body().code == 1) {
                            String bodyJson = new Gson().toJson(response.body());
                            Log.d("HistoryActivity", "响应体 JSON: " + bodyJson);
                            Log.d("HistoryActivity", "业务 code: " + response.body().code);
                            Log.d("HistoryActivity", "业务 msg: " + response.body().msg);
                            HistoryResponse.HistoryData data = response.body().data;
                            if (data != null) {
                                List<ScenicSpot> newList = data.list;
                                if (newList != null && !newList.isEmpty()) {
                                    Log.d(TAG, "nextLastId=" + data.nextLastId + ", list size=" + newList.size());
                                    for (ScenicSpot spot : newList) {
                                        Log.d(TAG, "item id=" + spot.getId() + ", conversationId=" + spot.getConversationId());
                                        if (SpUtils.isRated(HistoryActivity.this, spot.getConversationId())) {
                                            spot.setEnded(true);
                                        }
                                    }
                                    lastId = data.nextLastId;
                                    hasMore = data.hasMore;
                                    isLoading = false;
                                    adapter.addData(newList);
                                    updateEmptyState();
                                    updateCityTags();
                                    maybeLoadMoreIfNeeded();
                                } else {
                                    isLoading = false;
                                    updateEmptyState();
                                    Toast.makeText(HistoryActivity.this, "没有更多了", Toast.LENGTH_SHORT).show();
                                    hasMore = false;
                                }
                            } else {
                                isLoading = false;
                            }
                        } else {
                            isLoading = false;
                            hasMore = false;
                            HistoryResponse body = response.body();
                            if (body == null) {
                                Log.e("HistoryActivity", "景点请求失败: 响应体为 null, HTTP状态码=" + response.code());
                                Toast.makeText(HistoryActivity.this, "景点请求失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String bodyJson = new Gson().toJson(body);
                            Log.e("HistoryActivity","景点请求失败:" + body.msg);
                            Log.e("HistoryActivity", "响应体 JSON: " + bodyJson);
                            Log.e("HistoryActivity", "业务 code: " + body.code);
                            Log.e("HistoryActivity", "业务 msg: " + body.msg);
                            Toast.makeText(HistoryActivity.this,"景点请求失败",Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<HistoryResponse> call, Throwable t) {
                        if (seq != loadSeq) {
                            return;
                        }
                        isLoading = false;
                        swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void maybeLoadMoreIfNeeded() {
        if (isLoading || !hasMore || rvScenic == null) return;
        GridLayoutManager layoutManager = (GridLayoutManager) rvScenic.getLayoutManager();
        if (layoutManager == null) return;
        int visibleItemCount = layoutManager.getChildCount();
        int totalItemCount = layoutManager.getItemCount();
        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        if ((visibleItemCount + firstVisiblePosition) >= totalItemCount - 3) {
            loadNextPage();
        }
    }

    private void setupTags() {
        // 初始化城市标签 View 数组（复用原有类型标签 View）
        cityTagViews[0] = findViewById(R.id.tag_theme_park);
        cityTagViews[1] = findViewById(R.id.tag_museum);
        cityTagViews[2] = findViewById(R.id.tag_nature_park);
        cityTagViews[3] = findViewById(R.id.tag_scenic);
        cityTagViews[4] = findViewById(R.id.tag_history);
        cityTagViews[5] = findViewById(R.id.tag_ally);
        cityTagViews[6] = findViewById(R.id.tag_zoo);
        cityTagViews[7] = findViewById(R.id.tag_morden);

        for (TextView tv : cityTagViews) {
            tv.setVisibility(View.GONE);
        }

        // 为每个城市标签设置点击事件
        for (int i = 0; i < cityTagViews.length; i++) {
            final int index = i;
            cityTagViews[i].setOnClickListener(v -> {
                if (index < cityNames.length) {
                    currentCity = cityNames[index];
                    // 清除其他标签选中状态
                    findViewById(R.id.tag_all).setSelected(false);
                    for (TextView tv : cityTagViews) tv.setSelected(false);
                    v.setSelected(true);
                    resetAndLoad();
                }
            });
        }

        // "全部"标签——清除城市筛选和关键词搜索，回到全部显示
        TextView tagAll = findViewById(R.id.tag_all);
        tagAll.setOnClickListener(v -> {
            currentCity = null;
            currentKeyWord = null;
            etSearch.setText("");
            tagAll.setSelected(true);
            for (TextView tv : cityTagViews) tv.setSelected(false);
            resetAndLoad();
        });
        tagAll.setSelected(true);
    }

    /** 从已加载数据中提取城市名，更新标签（仅在无城市筛选时更新，保留完整城市列表） */
    private void updateCityTags() {
        if (currentCity != null) return; // 有筛选时不更新标签列表

        // 收集唯一城市名
        java.util.Set<String> citySet = new java.util.LinkedHashSet<>();
        for (ScenicSpot spot : dataList) {
            String c = spot.getCity();
            if (c != null && !c.isEmpty()) {
                citySet.add(c);
            }
        }
        if (citySet.isEmpty()) return;

        cityNames = citySet.toArray(new String[0]);

        // 更新标签文字和可见性
        for (int i = 0; i < cityTagViews.length; i++) {
            if (i < cityNames.length) {
                cityTagViews[i].setText(cityNames[i]);
                cityTagViews[i].setVisibility(View.VISIBLE);
            } else {
                cityTagViews[i].setVisibility(View.GONE);
            }
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                new AttractionPickerDialog().show(getSupportFragmentManager(), "AttractionPicker");
            } else {
                Toast.makeText(this, "需要定位权限才能获取附近景点", Toast.LENGTH_SHORT).show();
                // 即使拒绝也打开对话框（会用北京兜底）
                new AttractionPickerDialog().show(getSupportFragmentManager(), "AttractionPicker");
            }
        }
    }

    private void initView() {
        ivAdd=findViewById(R.id.iv_add_chat);
        tvHistory=findViewById(R.id.tv_history);
        tvMine=findViewById(R.id.tv_mine);
        // 活跃标签指示器已在布局 XML 中通过 bg_nav_active 设置
        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvScenic = findViewById(R.id.rv_scenic);
        etSearch = findViewById(R.id.et_search);
        tvSortTime = findViewById(R.id.tv_sort_time);
        layoutEmpty = findViewById(R.id.layout_empty);

        // 下拉刷新
        swipeRefresh.setColorSchemeResources(
                R.color.profile_primary,
                R.color.login_on_primary,
                R.color.profile_star_yellow
        );
        swipeRefresh.setOnRefreshListener(() -> {
            // 下拉时清空筛选条件，重新加载
            currentKeyWord = null;
            currentCity = null;
            etSearch.setText("");
            findViewById(R.id.tag_all).setSelected(true);
            for (TextView tv : cityTagViews) tv.setSelected(false);
            resetAndLoad();
        });

        tvHistory.setOnClickListener(v -> {
           Toast.makeText(this,"当前已是旅游历史页面",Toast.LENGTH_SHORT).show();
        });
        tvMine.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        // 初始空状态
        updateEmptyState();

        ivAdd.setOnClickListener(v->{
            if (hasLocationPermission()) {
                new AttractionPickerDialog().show(getSupportFragmentManager(), "AttractionPicker");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            }
        });
    }


}