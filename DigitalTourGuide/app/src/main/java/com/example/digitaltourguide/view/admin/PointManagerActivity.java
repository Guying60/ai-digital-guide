package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import android.app.Dialog;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.AttractionListAdapter;
import com.example.digitaltourguide.model.admin.AttractionListData;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.AddAttractionRequest;
import com.example.digitaltourguide.model.admin.BatchDeleteRequest;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PointManagerActivity extends AppCompatActivity {
    private static final String TAG="PointManagerActivity";
    private EditText etSearch;
    private RecyclerView rvScenic;
    private TextView tvDelete, tvCancel, tvBatchDelete;
    private MaterialButton tvAdd;
    private boolean isBatchMode = false;
    private AttractionListAdapter adapter;
    private List<AddAttractionRequest> attractionList = new ArrayList<>();
    private String currentKeyword = "";
    private Integer currentType = null;
    private String nextLastId = null;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private String token;
    private ActivityResultLauncher<Intent> editAttractionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_point_manager);

        initViews();
        token = SpUtils.getAdminToken(this);
        setupRecyclerView();
        setupListeners();
        loadFirstPage();

        editAttractionLauncher=registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result->{
                    if(result.getResultCode() == RESULT_OK){
                        //新增景点成功，刷新列表
                        loadFirstPage();
                    }
                }
        );

    }

    private void setupRecyclerView() {
        adapter = new AttractionListAdapter(attractionList, new AttractionListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(AddAttractionRequest item) {
                Intent intent = new Intent(PointManagerActivity.this, DataAnalysisActivity.class);
                intent.putExtra("attraction_id", item.getId());
                startActivity(intent);
            }
            @Override
            public void onEditClick(AddAttractionRequest item) {
                //点击编辑，跳转到编辑景点页面
                Intent intent=new Intent(PointManagerActivity.this, ScenicEditActivity.class);
                intent.putExtra("attraction_id",item.getId());
                editAttractionLauncher.launch(intent);
            }
            @Override
            public void onDeleteClick(AddAttractionRequest item) {
                Dialog dialog = new Dialog(PointManagerActivity.this);
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
                    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                }

                TextView tvMessage = view.findViewById(R.id.tv_delete_message);
                if (tvMessage != null) {
                    tvMessage.setText("确定要删除景点【" + item.getAttractionName() + "】吗？删除后不可恢复。");
                }

                view.findViewById(R.id.btn_cancel_delete).setOnClickListener(v -> dialog.dismiss());
                view.findViewById(R.id.btn_confirm_delete).setOnClickListener(v -> {
                    dialog.dismiss();
                    deleteAttraction(item);
                });

                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
            }

            @Override
            public void onItemSelected(AddAttractionRequest item, boolean isSelected) {

            }
        });
        rvScenic.setLayoutManager(new GridLayoutManager(this, 1));
        rvScenic.setAdapter(adapter);

        rvScenic.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (!isLoading && hasMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                    loadNextPage();
                }
            }
        });

        // 批量删除按钮（假设 id 为 tv_batch_delete）
       tvBatchDelete.setOnClickListener(v -> {
            if (isBatchMode) {
                // 已经处于多选模式，执行批量删除
                confirmBatchDelete();
            } else {
                // 进入多选模式
                enterBatchMode();
            }
        });
    }

    private void confirmBatchDelete() {
        List<String> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请至少选择一个景点", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this);
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
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView tvMessage = view.findViewById(R.id.tv_delete_message);
        if (tvMessage != null) {
            tvMessage.setText("确定要删除选中的 " + selectedIds.size() + " 个景点吗？");
        }

        view.findViewById(R.id.btn_cancel_delete).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_confirm_delete).setOnClickListener(v -> {
            dialog.dismiss();
            callBatchDeleteApi(selectedIds);
        });

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void enterBatchMode() {
        isBatchMode = true;
        adapter.setMultiSelectMode(true);
        tvBatchDelete.setText("确认删除");
        // 新增按钮 → 取消（隐藏图标）
        tvAdd.setText("取消");
        tvAdd.setIconResource(0);
        if (tvCancel != null) tvCancel.setVisibility(View.GONE);
    }

    private void exitBatchMode() {
        isBatchMode = false;
        adapter.setMultiSelectMode(false);
        tvBatchDelete.setText("批量删除");
        // 取消 → 恢复新增景点（带图标）
        tvAdd.setText(R.string.pm_add);
        tvAdd.setIconResource(R.drawable.ic_add_circle);
        if (tvCancel != null) tvCancel.setVisibility(View.GONE);
    }

    private void callBatchDeleteApi(List<String> ids) {
        // 注意：接口文档中 ids 是 List<Long>，但雪花 ID 较大，后端可能要求 Long 类型
        // 前端需将 String 转换为 Long，或者直接传 String（取决于后端实际接收类型）
        // 这里假设后端能接收 String，如果不能，则转换为 Long
        List<String> longIds = new ArrayList<>();
        for (String id : ids) {
            longIds.add(id);
        }

        BatchDeleteRequest request = new BatchDeleteRequest(ids);
        RetrofitClient.getAdminApiService()
                .batchDeleteAttractions(request)
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<Void> result = response.body();
                            if (result.getCode() == 1) {
                                Toast.makeText(PointManagerActivity.this, "批量删除成功", Toast.LENGTH_SHORT).show();
                                exitBatchMode();
                                loadFirstPage();  // 刷新列表
                            } else {
                                Toast.makeText(PointManagerActivity.this, "删除失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(PointManagerActivity.this, "请求失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        Toast.makeText(PointManagerActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteAttraction(AddAttractionRequest item) {
        String attractionId = item.getId();
        token = SpUtils.getAdminToken(this);

        RetrofitClient.getAdminApiService()
                .deleteAttraction("Bearer " + token, attractionId)
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<Void> result = response.body();
                            if (result.getCode() == 1) {
                                Toast.makeText(PointManagerActivity.this, "删除成功", Toast.LENGTH_SHORT).show();
                                // 从本地列表中移除并刷新
                                attractionList.remove(item);
                                adapter.notifyDataSetChanged();
                            } else {
                                Log.d(TAG,"result code="+result.getCode());
                                Toast.makeText(PointManagerActivity.this, "删除失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(PointManagerActivity.this, "请求失败：" + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        Toast.makeText(PointManagerActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupListeners() {
        // 设置图标 → 弹出管理菜单
        findViewById(R.id.iv_settings).setOnClickListener(v -> showAdminMenuDialog());

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            search();
            return true;
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
        //其他类型的标签
        int[] tagIds = {R.id.tag_theme_park, R.id.tag_museum, R.id.tag_nature_park,
                R.id.tag_scenic, R.id.tag_history, R.id.tag_ally, R.id.tag_zoo, R.id.tag_morden};
        for (int id : tagIds) {
            TextView tag = findViewById(id);
            tag.setOnClickListener(v -> {
                for (int i : tagIds) {
                    findViewById(i).setSelected(false);
                }
                v.setSelected(true);
                currentType = getTypeByTagText(((TextView) v).getText().toString());
                loadFirstPage();
            });
        }

        tvAdd.setOnClickListener(v -> {
            if (isBatchMode) {
                exitBatchMode();
            } else {
                Toast.makeText(this, "新增景点", Toast.LENGTH_SHORT).show();
                Intent intent=new Intent(PointManagerActivity.this, ScenicEditActivity.class);
                editAttractionLauncher.launch(intent);
            }
        });

    }

    private Integer getTypeByTagText(String text) {
        switch (text) {
            case "主题乐园": return 0;
            case "博物馆与展馆": return 1;
            case "自然公园": return 2;
            case "风景名胜与休闲度假": return 3;
            case "历史文化": return 4;
            case "古镇水乡": return 5;
            case "动植物园与水族馆": return 6;
            case "现代地标": return 7;
            default: return null;
        }
    }

    private void search() {
        String keyword = etSearch.getText().toString().trim();
        currentKeyword = keyword;
        loadFirstPage();
    }

    private void loadFirstPage() {
        nextLastId = null;
        hasMore = true;
        attractionList.clear();
        adapter.notifyDataSetChanged();
        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoading) return;
        if (!hasMore && nextLastId != null) return;

        isLoading = true;
        RetrofitClient.getAdminApiService()
                .getAttractionList("Bearer " + token, currentKeyword, currentType, nextLastId, 6)
                .enqueue(new Callback<BaseResponse<AttractionListData>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<AttractionListData>> call, Response<BaseResponse<AttractionListData>> response) {
                        Log.d(TAG, "code=" + response.code());
                        Log.d(TAG, "body=" + response.body());
                        isLoading = false;
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<AttractionListData> result = response.body();
                            if (result.getCode() == 1 && result.getData() != null) {
                                AttractionListData data = result.getData();
                                Log.d(TAG, "list size=" + (data.getList() == null ? 0 : data.getList().size()));
                                Log.d(TAG, "nextLastId=" + data.getNextLastId());
                                Log.d(TAG, "hasMore=" + data.isHasMore());
                                List<AddAttractionRequest> newList = data.getList();
                                if (newList != null && !newList.isEmpty()) {
                                    attractionList.addAll(newList);
                                    adapter.notifyDataSetChanged();
                                }
                                nextLastId = data.getNextLastId();
                                hasMore = data.isHasMore();
                            } else {
                                Toast.makeText(PointManagerActivity.this, "加载失败：" + result.getMsg(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(PointManagerActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<AttractionListData>> call, Throwable t) {
                        isLoading = false;
                        Toast.makeText(PointManagerActivity.this, "网络异常：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 显示管理菜单弹窗（管理模式、管理信息、退出登录）
     */
    private void showAdminMenuDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_admin_menu);

        // 设置管理员账号（从 SpUtils 读取）
        TextView tvAdminName = (TextView) dialog.findViewById(R.id.tv_admin_name);
        String adminUsername = SpUtils.getAdminUsername(this);
        if (!adminUsername.isEmpty()) {
            tvAdminName.setText("管理员：" + adminUsername);
        }

        // 管理模式
        dialog.findViewById(R.id.menu_management_mode).setOnClickListener(v -> {
            Toast.makeText(this, "管理模式", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // 管理信息
        dialog.findViewById(R.id.menu_management_info).setOnClickListener(v -> {
            Toast.makeText(this, "管理信息", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // 退出登录
        dialog.findViewById(R.id.menu_logout).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(PointManagerActivity.this, AdminLoginActivity.class));
            finishAffinity();  // 清除所有 Activity 栈
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void initViews() {
        etSearch = findViewById(R.id.et_search);
        rvScenic = findViewById(R.id.rv_scenic);
        tvAdd = findViewById(R.id.tv_add);
        // 初始状态下 MaterialButton 会自动从 XML 的 app:icon 加载图标
        tvBatchDelete=findViewById(R.id.tv_delete_batch);
    }
}
