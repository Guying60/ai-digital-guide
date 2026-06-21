package com.example.digitaltourguide.view.user;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.adapter.AttractionPickerAdapter;
import com.example.digitaltourguide.model.user.AttractionPage;
import com.example.digitaltourguide.model.user.ScenicSpot;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AttractionPickerDialog extends DialogFragment {
    private EditText etSearch;
    private RecyclerView rvAttractions;
    private AttractionPickerAdapter adapter;
    private String currentKeyword = "";
    private String nextLastId = null;//最后一条数据的id
    private boolean isLoading = false;//表示当前正在加载数据，防止用户快速滑动重复触发加载请求
    private boolean hasMore = true;//是否还有数据可以加载
    private static final int PAGE_SIZE = 10;
    private List<ScenicSpot> spotList=new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_attraction_list, container, false);
        etSearch = view.findViewById(R.id.et_search);
        rvAttractions = view.findViewById(R.id.rv_attractions);

        rvAttractions.setLayoutManager(new GridLayoutManager(getContext(),2));
        adapter = new AttractionPickerAdapter(getContext(),spotList);
        adapter.setOnItemClickListener(spot -> {
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra("attractionId", spot.getId());
            startActivity(intent);
            dismiss();
        });
        rvAttractions.setAdapter(adapter);

        // 搜索框文本变化监听
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentKeyword = s.toString();
                resetAndLoad();
            }
        });
        //滚动到底部加载更多
        rvAttractions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                //该方法为在滚动中持续触发的回调方法
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                int total = adapter.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();//最后一个可见条目
                if (!isLoading && hasMore && lastVisible >= total - 2) {
                    //当最后一个可见条目的位置》=总条目数-2就触发加载
                    loadMore();
                }
            }
        });

        loadFirstPage();
        return view;
    }

    //重置分页状态并重新加载第一页数据
    private void resetAndLoad(){
        nextLastId=null;
        hasMore=true;
        spotList.clear();
        adapter.notifyDataSetChanged();//清空当前数据等待加载
        loadFirstPage();
    }

    //请求第一页数据
    private void loadFirstPage() {
        loadData(null);
    }

    private void loadMore(){
        if(nextLastId!=null && isLoading){
            loadData(nextLastId);
        }
    }


    private void loadData(String lastId) {
        if(isLoading) return;
        isLoading=true;

        String token= SpUtils.getUserToken(getContext());
         RetrofitClient.getApiService()
                .getAttractions(null, null, null, currentKeyword, null, lastId, PAGE_SIZE)
                 .enqueue(new Callback<AttractionPage>() {
                     @Override
                     public void onResponse(Call<AttractionPage> call, Response<AttractionPage> response) {
                         isLoading=false;
                         // 打印 HTTP 状态码
                         Log.d("AttractionPicker", "HTTP code: " + response.code());
                         if(response.isSuccessful() && response.body()!=null){
                             AttractionPage page= response.body();
                             List<ScenicSpot> newSpots=page.getList();
                             if(newSpots==null) newSpots=new ArrayList<>();

                             if(lastId==null){
                                 //第一页加载，清空旧数据先
                                 spotList.clear();
                                 spotList.addAll(newSpots);
                             }else{
                                 //下一页加载，直接把新数据加在旧数据后面
                                 spotList.addAll(newSpots);
                             }
                             adapter.notifyDataSetChanged();
                             nextLastId= page.getNextLastId();//拿到下一页的游标id
                             hasMore=page.isHasMore();
                         }else{
                             // 获取错误体内容
                             String errorBody = "";
                             try {
                                 if (response.errorBody() != null) {
                                     errorBody = response.errorBody().string();
                                     Log.e("AttractionPicker", "Error body: " + errorBody);
                                 }
                             } catch (Exception e) {
                                 e.printStackTrace();
                             }
                             String errorMsg = "加载失败: HTTP " + response.code() + (errorBody.isEmpty() ? "" : " - " + errorBody);
                             Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                             return;
                         }
                     }
                     @Override
                     public void onFailure(Call<AttractionPage> call, Throwable t) {
                            isLoading=false;
                            Toast.makeText(getContext(),"网络错误："+t.getMessage(),Toast.LENGTH_SHORT).show();
                     }
                 });
    }

    @Override
    public void onResume() {
        super.onResume();
        if(getDialog()!=null && getDialog().getWindow()!=null){
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
