package com.example.digitaltourguide.view.user;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.ReviewAdapter;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.ReviewPage;
import com.example.digitaltourguide.model.user.SubmitReviewRequest;
import com.example.digitaltourguide.model.user.UserReviewItem;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyJudgeActivity extends AppCompatActivity {

    private RecyclerView rvReviews;
    private ReviewAdapter adapter;
    private ArrayList<UserReviewItem> reviewList = new ArrayList<>();

    // Filter chips
    private TextView filterAll, filterReviewed, filterPending;
    private int currentFilterStatus = -1; // -1=全部, 0=待评价, 1=已评价

    // Edit mode
    private TextView tvEdit;
    private boolean isEditMode = false;

    // Pagination
    private String nextLastId = null;
    private boolean hasMore = true;
    private boolean isLoading = false;
    private static final int PAGE_SIZE = 10;

    // Footer
    private LinearLayout footerLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_judge);

        SpUtils.init(this);

        initView();
        loadReviews(null, currentFilterStatus);
    }

    private void initView() {
        rvReviews = findViewById(R.id.rv_reviews);
        footerLayout = findViewById(R.id.footer_layout);

        filterAll = findViewById(R.id.filter_all);
        filterReviewed = findViewById(R.id.filter_reviewed);
        filterPending = findViewById(R.id.filter_pending);

        tvEdit = findViewById(R.id.tv_edit_reviews);

        // 返回箭头
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                startActivity(new Intent(MyJudgeActivity.this, MyActivity.class));
                finish();
            });
        }

        // RecyclerView 设置
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReviewAdapter(reviewList);
        rvReviews.setAdapter(adapter);

        // 点击删除
        adapter.setOnDeleteClickListener((item, position) -> showDeleteConfirmDialog(item, position));

        // 点击去评价
        adapter.setOnRateClickListener((item, position) -> showRatingDialog(item, position));

        // Filter chips
        filterAll.setOnClickListener(v -> setFilter(-1));
        filterReviewed.setOnClickListener(v -> setFilter(1));
        filterPending.setOnClickListener(v -> setFilter(0));

        // 编辑模式切换
        tvEdit.setOnClickListener(v -> {
            isEditMode = !isEditMode;
            tvEdit.setText(isEditMode ? "完成" : "编辑");
            // 编辑模式下，待评价条目也显示删除按钮
            adapter.notifyDataSetChanged();
        });

        // 分页滚动加载
        rvReviews.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int total = adapter.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (!isLoading && hasMore && lastVisible >= total - 2) {
                    loadMore();
                }
            }
        });
    }

    private void setFilter(int status) {
        currentFilterStatus = status;
        resetAndLoad();
        updateChipStyle();
    }

    private void updateChipStyle() {
        int selectedBg = R.drawable.bg_chip_filter_selected;
        int unselectedBg = R.drawable.bg_chip_filter_unselected;
        int selectedText = R.color.profile_on_primary;
        int unselectedText = R.color.profile_on_surface_variant;

        filterAll.setBackgroundResource(currentFilterStatus == -1 ? selectedBg : unselectedBg);
        filterAll.setTextColor(getColor(currentFilterStatus == -1 ? selectedText : unselectedText));

        filterReviewed.setBackgroundResource(currentFilterStatus == 1 ? selectedBg : unselectedBg);
        filterReviewed.setTextColor(getColor(currentFilterStatus == 1 ? selectedText : unselectedText));

        filterPending.setBackgroundResource(currentFilterStatus == 0 ? selectedBg : unselectedBg);
        filterPending.setTextColor(getColor(currentFilterStatus == 0 ? selectedText : unselectedText));
    }

    private void resetAndLoad() {
        nextLastId = null;
        hasMore = true;
        reviewList.clear();
        adapter.notifyDataSetChanged();
        loadReviews(null, currentFilterStatus);
    }

    private void loadMore() {
        if (nextLastId != null && !isLoading) {
            loadReviews(nextLastId, currentFilterStatus);
        }
    }

    private void loadReviews(String lastId, Integer status) {
        // status=-1 表示"全部"，不传参
        Integer queryStatus = (status != null && status == -1) ? null : status;
        if (isLoading) return;
        isLoading = true;

        RetrofitClient.getApiService()
                .getReviews(lastId, PAGE_SIZE, queryStatus)
                .enqueue(new Callback<BaseResponse<ReviewPage>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<ReviewPage>> call, Response<BaseResponse<ReviewPage>> response) {
                        isLoading = false;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 1 && response.body().getData() != null) {
                            ReviewPage page = response.body().getData();
                            List<UserReviewItem> newList = page.getList();
                            if (newList == null) newList = new ArrayList<>();

                            if (lastId == null) {
                                reviewList.clear();
                            }
                            reviewList.addAll(newList);
                            adapter.notifyDataSetChanged();

                            nextLastId = page.getNextLastId();
                            hasMore = page.isHasMore();

                            // 更新 footer
                            updateFooter();
                        } else {
                            String msg = response.body() != null ? response.body().getMsg() : "加载失败";
                            Toast.makeText(MyJudgeActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<ReviewPage>> call, Throwable t) {
                        isLoading = false;
                        Toast.makeText(MyJudgeActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateFooter() {
        if (footerLayout == null) return;
        if (!hasMore && !reviewList.isEmpty()) {
            footerLayout.setVisibility(View.VISIBLE);
        } else {
            footerLayout.setVisibility(View.GONE);
        }
    }

    // ──────────────────────────────────────
    //  删除确认弹窗
    // ──────────────────────────────────────
    private void showDeleteConfirmDialog(UserReviewItem item, int position) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_delete_confirm);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.82),
                WindowManager.LayoutParams.WRAP_CONTENT
        );

        TextView tvMessage = dialog.findViewById(R.id.tv_delete_message);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel_delete);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm_delete);

        if (tvMessage != null) {
            tvMessage.setText("确定要删除该评价吗？");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            deleteReview(item, position);
        });

        dialog.show();
    }

    private void deleteReview(UserReviewItem item, int position) {
        RetrofitClient.getApiService()
                .deleteReview(item.getId())
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 1) {
                            Toast.makeText(MyJudgeActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                            reviewList.remove(position);
                            adapter.notifyItemRemoved(position);
                            updateFooter();
                        } else {
                            String msg = response.body() != null ? response.body().getMsg() : "删除失败";
                            Toast.makeText(MyJudgeActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        Toast.makeText(MyJudgeActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ──────────────────────────────────────
    //  提交评价弹窗
    // ──────────────────────────────────────
    private float selectedRating = 5.0f;
    private final List<String> selectedTags = new ArrayList<>();

    private void showRatingDialog(UserReviewItem item, int position) {
        selectedRating = 5.0f;
        selectedTags.clear();

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_rating);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                WindowManager.LayoutParams.WRAP_CONTENT
        );

        // 星星
        ImageView[] stars = new ImageView[5];
        stars[0] = dialog.findViewById(R.id.star_1);
        stars[1] = dialog.findViewById(R.id.star_2);
        stars[2] = dialog.findViewById(R.id.star_3);
        stars[3] = dialog.findViewById(R.id.star_4);
        stars[4] = dialog.findViewById(R.id.star_5);

        for (int i = 0; i < 5; i++) {
            final int index = i;
            stars[i].setOnClickListener(v -> {
                selectedRating = index + 1;
                updateStarDisplay(stars, (int) selectedRating);
                // 根据评分自动设置标签
                updateTagsByRating((int) selectedRating);
            });
        }
        // 默认 5 星
        updateStarDisplay(stars, 5);
        updateTagsByRating(5);

        // 可点击标签
        final int[] tagIds = {
                R.id.tag_professional, R.id.tag_rich, R.id.tag_fun,
                R.id.tag_clear, R.id.tag_knowledgeable, R.id.tag_excellent
        };
        for (int id : tagIds) {
            TextView tag = dialog.findViewById(id);
            if (tag != null) {
                tag.setTag("unselected");
                tag.setOnClickListener(v -> {
                    if ("unselected".equals(tag.getTag())) {
                        if (selectedTags.size() < 5) {
                            tag.setTag("selected");
                            tag.setBackgroundResource(R.drawable.bg_chip_filter_selected);
                            tag.setTextColor(getColor(R.color.profile_on_primary));
                            selectedTags.add(tag.getText().toString());
                        } else {
                            Toast.makeText(this, "最多选择 5 个标签", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        tag.setTag("unselected");
                        tag.setBackgroundResource(R.drawable.bg_tag_rating);
                        tag.setTextColor(getColor(R.color.profile_primary));
                        selectedTags.remove(tag.getText().toString());
                    }
                });
            }
        }

        // 输入框
        EditText editFeedback = dialog.findViewById(R.id.edit_feedback);
        TextView tvCharCount = dialog.findViewById(R.id.tv_char_count);

        if (editFeedback != null && tvCharCount != null) {
            editFeedback.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    tvCharCount.setText(s.length() + "/500");
                }
            });
        }

        // 按钮
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSubmit = dialog.findViewById(R.id.btn_submit);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String content = editFeedback != null ? editFeedback.getText().toString().trim() : "";
            if (content.isEmpty()) {
                Toast.makeText(this, "请填写评价内容", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            submitReview(item, (int) selectedRating, content, new ArrayList<>(selectedTags), position);
        });

        dialog.show();
    }

    private void updateStarDisplay(ImageView[] stars, int count) {
        int goldColor = getColor(R.color.profile_star_yellow);
        int grayColor = getColor(R.color.profile_outline_variant);
        for (int i = 0; i < 5; i++) {
            boolean selected = i < count;
            stars[i].setImageResource(selected
                    ? R.drawable.ic_star_filled
                    : R.drawable.ic_star_outline);
            stars[i].setColorFilter(selected ? goldColor : grayColor);
        }
    }

    private void updateTagsByRating(int rating) {
        // 评分变化时自动切换标签选中状态（由用户手动选择，这里不做自动切换）
    }

    private void submitReview(UserReviewItem item, int rating, String content, List<String> tags, int position) {
        SubmitReviewRequest request = new SubmitReviewRequest(
                item.getId(),
                rating,
                content,
                tags
        );

        RetrofitClient.getApiService()
                .submitReview(request)
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getCode() == 1) {
                            Toast.makeText(MyJudgeActivity.this, "评价成功", Toast.LENGTH_SHORT).show();
                            // 刷新当前项
                            resetAndLoad();
                        } else {
                            String msg = response.body() != null ? response.body().getMsg() : "提交失败";
                            Toast.makeText(MyJudgeActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        Toast.makeText(MyJudgeActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}