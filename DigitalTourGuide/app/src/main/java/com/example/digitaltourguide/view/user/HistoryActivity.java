package com.example.digitaltourguide.view.user;

import android.app.Dialog;
import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private EditText etSearch;
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
    private Integer currentType = null;     // 没选择时为 null
    private ApiService apiService;
    private String token;
    private int currentClickPosition = -1;
   private List<ScenicSpot> list;
   private ImageView ivAdd;
   private LinearLayout tvHistory,tvMine;
    private UserScenicAdapter.OnItemClickListener listener;

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
    private void initNetwork() {
        token = SpUtils.getUserToken(this);
        if (!token.startsWith("Bearer ")) {
            token = "Bearer " + token;  // 根据你拦截器的情况调整
        }
        apiService = RetrofitClient.getApiService();
    }

    private void setupRecyclerView() {
        rvScenic.setLayoutManager(new GridLayoutManager(this, 1));
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
                // 结束对话：可调用后端接口标记对话结束，并更新本地状态
                if (listener != null) {
                    listener.onStopChatClick(spot);
                }
                // 修改状态并刷新UI
                spot.setEnded(true);
                adapter.notifyDataSetChanged();
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

        // ── 评分标签 (点击高亮切换) ──
        int[] tagIds = {
                R.id.tag_professional, R.id.tag_rich, R.id.tag_fun,
                R.id.tag_clear, R.id.tag_knowledgeable, R.id.tag_excellent
        };
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
            });
        }

        // ── 评论文本 + 字数统计 ──
        EditText editFeedback = view.findViewById(R.id.edit_feedback);
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
                    i < score ? R.color.profile_primary : R.color.profile_outline_variant
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

        //全部的标签
        TextView tagAll=findViewById(R.id.tag_all);
        tagAll.setOnClickListener(v->{
            // 清除所有其他标签的选中状态
            int[] tagIds = {R.id.tag_theme_park, R.id.tag_museum, R.id.tag_nature_park,
                    R.id.tag_scenic, R.id.tag_history, R.id.tag_ally,
                    R.id.tag_zoo, R.id.tag_morden};
            for (int id : tagIds) {
                findViewById(id).setSelected(false);
            }
            // 设置全部标签为选中
            v.setSelected(true);
            // 清除类型筛选条件
            currentType = null;
            // 重新加载第一页（显示所有景点）
            loadFirstPage();
        });
    }

    private void resetAndLoad() {
        lastId = null;
        hasMore = true;
        adapter.clearData();
        loadFirstPage();
    }

    private void loadFirstPage() {
        token = SpUtils.getUserToken(this);
        Log.d("HistoryActivity", "此时用户token: " + token);
        isLoading = true;
        apiService.getTourHistory(currentKeyWord, currentType, null, null, pageSize)
                .enqueue(new Callback<HistoryResponse>() {
                    @Override
                    public void onResponse(Call<HistoryResponse> call, Response<HistoryResponse> response) {
                        isLoading = false;
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
                                    adapter.addData(uniqueList);
                                    lastId = data.nextLastId;
                                    hasMore = data.hasMore;
                                } else {
                                    // 搜索结果为空
                                    Toast.makeText(HistoryActivity.this, "未找到相关景点", Toast.LENGTH_SHORT).show();
                                    lastId = null;
                                    hasMore = false;
                                }
                            }
                        } else {
                            HistoryResponse body = response.body();
                            if (body == null) {
                                Log.e("HistoryActivity", "景点请求失败: 响应体为 null");
                                Toast.makeText(HistoryActivity.this, "景点请求失败", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String bodyJson = new Gson().toJson(response.body());
                            Log.e("HistoryActivity","景点请求失败:"+response.body().msg);
                            Log.e("HistoryActivity", "响应体 JSON: " + bodyJson);
                            Log.e("HistoryActivity", "业务 code: " + response.body().code);
                            Log.e("HistoryActivity", "业务 msg: " + response.body().msg);
                            Toast.makeText(HistoryActivity.this,"景点请求失败",Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<HistoryResponse> call, Throwable t) {
                        isLoading = false;
                        Log.e("HistoryActivity", "网络请求失败: " + t.getMessage(), t);
                        Toast.makeText(HistoryActivity.this,"网络错误",Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadNextPage() {
        Log.d(TAG, "loadNextPage, lastId=" + lastId + ", hasMore=" + hasMore);
        token = SpUtils.getUserToken(this);
        if (!hasMore || isLoading) return;
        isLoading = true;
        apiService.getTourHistory(currentKeyWord, currentType, null, lastId, pageSize)
                .enqueue(new Callback<HistoryResponse>() {
                    @Override
                    public void onResponse(Call<HistoryResponse> call, Response<HistoryResponse> response) {
                        isLoading = false;
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
                                    adapter.addData(newList);
                                    lastId = data.nextLastId;
                                    hasMore = data.hasMore;
                                } else {
                                    // 没有更多数据
                                    Toast.makeText(HistoryActivity.this, "没有更多了", Toast.LENGTH_SHORT).show();
                                    hasMore = false;
                                }
                            }
                        } else {
                            hasMore = false; // 请求失败不再继续
                            String bodyJson = new Gson().toJson(response.body());
                            Log.e("HistoryActivity", "景点请求失败:" + response.body().msg);
                            Log.e("HistoryActivity", "响应体 JSON: " + bodyJson);
                            Log.e("HistoryActivity", "业务 code: " + response.body().code);
                            Log.e("HistoryActivity", "业务 msg: " + response.body().msg);
                            Toast.makeText(HistoryActivity.this, "景点请求失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<HistoryResponse> call, Throwable t) {
                        isLoading = false;
                    }
                });
    }

    private void setupTags() {
        // 主题乐园 -> type 0
        findViewById(R.id.tag_theme_park).setOnClickListener(v -> {
            currentType = 0;
            resetAndLoad();
        });
        // 自然公园 -> type 2 (注意：0主题乐园,1博物馆,2自然公园)
        findViewById(R.id.tag_museum).setOnClickListener(v -> {
            currentType = 2;
            resetAndLoad();
        });
        // 历史文化 -> type 4
        findViewById(R.id.tag_nature_park).setOnClickListener(v -> {
            currentType = 4;
            resetAndLoad();
        });
        // 网红打卡、美食探店 这两个类型后端枚举中没有，暂时传 null 或者未来扩展
        findViewById(R.id.tag_scenic).setOnClickListener(v -> {
            currentType = null; // 暂不筛选
            resetAndLoad();
        });
        findViewById(R.id.tag_history).setOnClickListener(v -> {
            currentType = null;
            resetAndLoad();
        });
        findViewById(R.id.tag_ally).setOnClickListener(v -> {
            currentType = null;
            resetAndLoad();
        });
        findViewById(R.id.tag_zoo).setOnClickListener(v -> {
            currentType = null;
            resetAndLoad();
        });
        findViewById(R.id.tag_morden).setOnClickListener(v -> {
            currentType = null;
            resetAndLoad();
        });
    }

    private void initView() {
        ivAdd=findViewById(R.id.iv_add_chat);
        tvHistory=findViewById(R.id.tv_history);
        tvMine=findViewById(R.id.tv_mine);
        tvHistory.setBackgroundResource(R.drawable.bg_nav_text_selected);
        tvMine.setBackgroundResource(R.drawable.bg_nav_text_selected);
        rvScenic = findViewById(R.id.rv_scenic);
        etSearch = findViewById(R.id.et_search);

        tvHistory.setOnClickListener(v -> {
           Toast.makeText(this,"当前已是旅游历史页面",Toast.LENGTH_SHORT).show();
        });
        tvMine.setOnClickListener(v -> {
            startActivity(new Intent(this, MyActivity.class));
        });
        ivAdd.setOnClickListener(v->{
            new AttractionPickerDialog().show(getSupportFragmentManager(), "AttractionPicker");
        });
    }


}