package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.ChatTrendData;
import com.example.digitaltourguide.model.admin.HotFaqItem;
import com.example.digitaltourguide.model.admin.HotFaqResponse;
import com.example.digitaltourguide.model.admin.SatisfactionTrendVO;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.HotFaqBarChart;
import com.example.digitaltourguide.utils.SpUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DataAnalysisActivity extends AppCompatActivity {
    private static final String TAG="DataAnalysisActivity";
    private Spinner spinnerQa,spinnerPeople,spinnerScore;
    private LineChart lineChart,lineChartSatisfaction;;
    private int currentDays = 1; // 默认昨日 (1天)
    private int satisfactionDays = 1; // 满意度趋势独立时间范围
    private LinearLayout tabTouristAnalysis;
    private TextView tvEmpty,tvTotalPeople,tvTouristText,tvDataText,tvBack;
    private DataTooltipMarkerView markerTrend, markerSatisfaction;
    private ImageView ivTouristIcon,ivDataIcon;
    private HotFaqBarChart hotFaqBarChart;
    private String currentAttractionId;

    String token;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_analyse);

        initView();

        token=SpUtils.getAdminToken(this);
        Log.d(TAG,"token:"+token);

        // 获取当前景区ID（实际应从登录成功后的存储或Intent获取）
        currentAttractionId = getIntent().getStringExtra("attraction_id");
        Log.d(TAG, "currentAttractionId = " + currentAttractionId);
        if (currentAttractionId == null || currentAttractionId.isEmpty()) {
            Toast.makeText(this, "未获取到景区ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupSpinner();

        spinnerQa.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override

            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int days=(position==0) ?7:30;
                loadHotFaqData(days);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        tabTouristAnalysis.setOnClickListener(v->{
            Intent intent = new Intent(DataAnalysisActivity.this, TouristAnalysisActivity.class);
            intent.putExtra("attraction_id", currentAttractionId);
            startActivity(intent);
            overridePendingTransition(R.anim.sibling_fade_in, R.anim.sibling_fade_out);
            finish();
        });

        tvBack.setOnClickListener(v->{
            finish();
        });
    }

    private void setupSpinner() {
        spinnerPeople.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                switch(position){
                    case 0: currentDays=1;break;
                    case 1: currentDays=7;break;
                    case 2: currentDays=30;break;
                }
                loadChatTrendData(currentDays);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        spinnerScore.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                switch(position){
                    case 0: satisfactionDays=1;break;
                    case 1: satisfactionDays=7;break;
                    case 2: satisfactionDays=30;break;
                }
                loadSatisfactionTrendData(satisfactionDays);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    private void loadSatisfactionTrendData(int days) {
        RetrofitClient.getAdminApiService()
                .getSatisfactionTrend("Bearer +"+token,currentAttractionId,days)
                .enqueue(new Callback<BaseResponse<SatisfactionTrendVO>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<SatisfactionTrendVO>> call, Response<BaseResponse<SatisfactionTrendVO>> response) {
Log.d(TAG, "satisfaction response code: " + response.code());
                        if (response.body() != null) {
                            Log.d(TAG, "satisfaction body: " + new Gson().toJson(response.body()));
                        } else if (response.errorBody() != null) {
                            try {
                                Log.e(TAG, "satisfaction error body: " + response.errorBody().string());
                            } catch (IOException e) { /* ... */ }
                        }

                        if(response.isSuccessful() && response.body()!=null && response.body().getCode()==1){
                            SatisfactionTrendVO data=response.body().getData();
                            drawSatisfactionLineChart(data);
                        }else{
                            showSatisfactionEmptyState("加载失败："+((response.body()!=null) ? response.body().getMsg():"未知错误"));
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<SatisfactionTrendVO>> call, Throwable t) {
showSatisfactionEmptyState("网络错误："+t.getMessage());
                    }
                });
    }

    private void showSatisfactionEmptyState(String msg) {
        lineChartSatisfaction.clear();
        lineChartSatisfaction.setNoDataText(msg);
        lineChartSatisfaction.invalidate();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void drawSatisfactionLineChart(SatisfactionTrendVO data) {
        if (data == null || data.getDates() == null || data.getDates().isEmpty()) {
            showSatisfactionEmptyState("暂无满意度数据");
            return;
        }
        List<String> dates = data.getDates();
        List<Double> avgScores = data.getAvgScores();

        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            entries.add(new Entry(i, avgScores.get(i).floatValue()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "满意度均分");
        dataSet.setColor(Color.parseColor("#FF9800"));
        dataSet.setCircleColor(Color.parseColor("#FF9800"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);

        LineData lineData = new LineData(dataSet);
        lineChartSatisfaction.setData(lineData);

        // X轴配置
        XAxis xAxis = lineChartSatisfaction.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dates));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        if (dates.size() > 6) {
            xAxis.setLabelRotationAngle(45f);
        }

        // Y轴：满意度范围0-5，可设置axisMaximum
        YAxis leftAxis = lineChartSatisfaction.getAxisLeft();
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(5f);

        lineChartSatisfaction.getDescription().setEnabled(false);
        lineChartSatisfaction.getAxisRight().setEnabled(false);
        lineChartSatisfaction.setTouchEnabled(true);
        lineChartSatisfaction.setDragEnabled(true);
        lineChartSatisfaction.animateX(500);
        lineChartSatisfaction.invalidate();

        // MarkerView
        markerSatisfaction = new DataTooltipMarkerView(this,
                dates.toArray(new String[0]), "满意度: ");
        List<Integer> countList = data.getCounts();
        int[] counts = new int[countList.size()];
        for (int i = 0; i < countList.size(); i++) counts[i] = countList.get(i);
        markerSatisfaction.setCounts(counts);
        lineChartSatisfaction.setMarker(markerSatisfaction);
    }

    //折线图
    private void loadChatTrendData(int days) {
        RetrofitClient.getAdminApiService()
                .getChatTrend("Bearer "+token,currentAttractionId,days)
                .enqueue(new Callback<BaseResponse<ChatTrendData>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<ChatTrendData>> call, Response<BaseResponse<ChatTrendData>> response) {
                        Log.d(TAG, "HTTP status code: " + response.code());
                        if (response.body() != null) {
                            Log.d(TAG, "Response body JSON: " + new Gson().toJson(response.body()));
                        } else {
                            Log.e(TAG, "Response body is null");
                            if (response.errorBody() != null) {
                                try {
                                    Log.e(TAG, "Error body: " + response.errorBody().string());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
if(response.isSuccessful()&& response.body()!=null){
                            BaseResponse<ChatTrendData> result=response.body();
                            if(result.getCode()==1 && result.getData()!=null){
                                ChatTrendData data=result.getData();
                                tvTotalPeople.setText("总人次："+data.getSummary().getTotalChats());
                                drawLineChart(data.getTrendList());
                            }else{
                                Toast.makeText(DataAnalysisActivity.this,"加载失败："+result.getMsg(),Toast.LENGTH_SHORT).show();
                            }
                        }else{
                            Log.e(TAG,"response.code="+response.code());
                            Toast.makeText(DataAnalysisActivity.this,"请求失败" ,Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<ChatTrendData>> call, Throwable t) {
Toast.makeText(DataAnalysisActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    //画折线图
    private void drawLineChart(List<ChatTrendData.TrendItem> trendList) {
        lineChart.setVisibility(View.VISIBLE);

        if(trendList==null || trendList.isEmpty()){
            lineChart.setNoDataText("暂无数据");
            lineChart.invalidate();//刷新显示
            return;
        }
        ArrayList<Entry> entries=new ArrayList<>();
        ArrayList<String> xLabels=new ArrayList<>();

        for (int i = 0; i < trendList.size(); i++) {
            ChatTrendData.TrendItem item=trendList.get(i);
            entries.add(new Entry(i,item.getCount()));
            xLabels.add(item.getTime());
        }

        LineDataSet dataSet = new LineDataSet(entries, "服务人次");
        dataSet.setColor(Color.parseColor("#4CAF50"));
        dataSet.setCircleColor(Color.parseColor("#4CAF50"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);

        LineData lineData=new LineData(dataSet);
        lineChart.setData(lineData);

        //x轴配置
        XAxis xAxis=lineChart.getXAxis();//创建x轴对象
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));//标签格式化
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//x轴放到底部
        if (xLabels.size() > 15) {
            // 近30天模式：每隔5天显示一个标签，避免拥挤
            xAxis.setGranularity(5f);
            xAxis.setLabelCount(6);
        } else {
            xAxis.setGranularity(1f);//最小刻度间隔为1
            xAxis.setLabelCount(xLabels.size());//标签的数量
        }
        if (xLabels.size() > 6) {
            xAxis.setLabelRotationAngle(45f);
        }// 如果标签数量超过 6 个，就让标签文字旋转 45°，避免文字挤在一起重叠

        YAxis leftAxis = lineChart.getAxisLeft();// 获取左侧 Y 轴对象
        leftAxis.setGranularity(1f);// 设置左侧 Y 轴的最小刻度间隔为 1，防止自动合并刻度
        lineChart.getAxisRight().setEnabled(false);// 禁用右侧 Y 轴

        //图表常规设置
        lineChart.getDescription().setEnabled(false);// 隐藏图表右下角的默认描述文字
        lineChart.setTouchEnabled(true);// 开启图表的触摸交互
        lineChart.setDragEnabled(true);// 开启图表的拖拽/平移功能（手指滑动可以查看超出屏幕的部分）
        lineChart.animateX(500);//从左到右展开图表，时间5毫秒

        // MarkerView 替代浮层
        markerTrend = new DataTooltipMarkerView(this,
                xLabels.toArray(new String[0]), "人次：");
        lineChart.setMarker(markerTrend);
        lineChart.invalidate();
    }

    private void loadHotFaqData(int days){
        hotFaqBarChart.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

       AdminApiService adminApiService = RetrofitClient.getAdminApiService();
       Call<HotFaqResponse> call = adminApiService.getHotFaq(currentAttractionId, days);
       Log.d(TAG, "Request URL: " + call.request().url().toString());
       call.enqueue(new Callback<HotFaqResponse>() {
           @Override
           public void onResponse(Call<HotFaqResponse> call, Response<HotFaqResponse> response) {
               Log.d(TAG, "response code: " + response.code());
               Log.d(TAG, "response body: " + new Gson().toJson(response.body()));
               if (response.isSuccessful() && response.body() != null) {
                   HotFaqResponse body = response.body();
                   if (body.getCode() == 1 && body.getData() != null && !body.getData().isEmpty()) {
                       showBarChart(body.getData());
                   } else {
                       String msg = (body.getMsg() != null && !body.getMsg().isEmpty()) ? body.getMsg() : "暂无数据";
                       showEmptyState(msg);
                   }
               } else {
                   try {
                       Log.e(TAG, "error body: " + response.errorBody().string()); // 需要处理 IOException
                   } catch (IOException e) {
                       throw new RuntimeException(e);
                   }
                   showEmptyState("请求失败，请稍后重试");
               }
           }
           @Override
           public void onFailure(Call<HotFaqResponse> call, Throwable t) {
               showEmptyState("网络错误：" + t.getMessage());
           }
       });
   }

   //展示空数据或错误信息
    private void showEmptyState(String message){
        hotFaqBarChart.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message);
    }

   //展示柱状图
    private void showBarChart(List<HotFaqItem> dataList){
        hotFaqBarChart.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        hotFaqBarChart.setData(dataList);
    }

    private void initView() {
        spinnerQa = findViewById(R.id.spinner_qa);
        tvEmpty = findViewById(R.id.tv_empty_qa);
        hotFaqBarChart = findViewById(R.id.hot_faq_bar_chart);
        tabTouristAnalysis = findViewById(R.id.tab_tourist_analysis);
        spinnerPeople=findViewById(R.id.spinner_people);
        tvTotalPeople = findViewById(R.id.tv_total_people);
        lineChart = findViewById(R.id.line_chart_people);
        ivTouristIcon=findViewById(R.id.iv_icon_tourist);
        tvTouristText=findViewById(R.id.tv_text_analysis);
        ivDataIcon=findViewById(R.id.iv_icon_data);
        tvDataText=findViewById(R.id.tv_text_data);
        tvBack=findViewById(R.id.tv_back);
        lineChartSatisfaction = findViewById(R.id.line_chart_satisfaction);
        spinnerScore = findViewById(R.id.spinner_score);

    }
}
