package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private Spinner spinnerQa,spinnerPeople;
    private ProgressBar progressBar;
    private LineChart lineChart,lineChartSatisfaction;;
    private int currentDays = 1; // 默认昨日 (1天)
    private View tooltipView; // 当前显示的卡片视图
    private LinearLayout tabTouristAnalysis;
    private TextView tvEmpty,tvTotalPeople,tooltipDate, tooltipCount,tvTouristText,tvDataText,tvBack;
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
        loadChatTrendData(currentDays);

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
        });

        tvBack.setOnClickListener(v->{
            Intent intent=new Intent(DataAnalysisActivity.this,PointManagerActivity.class);
            startActivity(intent);
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
                loadSatisfactionTrendData(currentDays);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
    }

    private void loadSatisfactionTrendData(int days) {
        progressBar.setVisibility(View.VISIBLE);
        RetrofitClient.getAdminApiService()
                .getSatisfactionTrend("Bearer +"+token,currentAttractionId,days)
                .enqueue(new Callback<BaseResponse<SatisfactionTrendVO>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<SatisfactionTrendVO>> call, Response<BaseResponse<SatisfactionTrendVO>> response) {
                        progressBar.setVisibility(View.GONE);
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
                        progressBar.setVisibility(View.GONE);
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

        // 可选：添加点击卡片显示评价数
        lineChartSatisfaction.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                if (index >= 0 && index < dates.size()) {
                    String date = dates.get(index);
                    double score = e.getY();
                    int count = data.getCounts().get(index);
                    showSatisfactionTooltip(h, date, score, count);
                }
            }
            @Override
            public void onNothingSelected() { hideTooltip(); }
        });
    }

    private void showSatisfactionTooltip(Highlight h, String date, double score, int count) {
        hideTooltip();
        if (tooltipView == null) {
            tooltipView = getLayoutInflater().inflate(R.layout.tooltip_card, null);
            tooltipDate = tooltipView.findViewById(R.id.tv_date);
            tooltipCount = tooltipView.findViewById(R.id.tv_count);
        }
        tooltipDate.setText(date);
        tooltipCount.setText(String.format("满意度: %.1f 分 (评价数: %d)", score, count));
        // 获取根布局（整个 Activity 的顶层视图）
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null) return;

        //计算数据点在屏幕上的绝对坐标
        float[] point = new float[]{h.getXPx(), h.getYPx()};
        int[] chartLocation = new int[2];
        lineChart.getLocationOnScreen(chartLocation);
        float rootX = chartLocation[0] + point[0];
        float rootY = chartLocation[1] + point[1];

        //测量卡片尺寸
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int cardWidth = tooltipView.getMeasuredWidth();
        int cardHeight = tooltipView.getMeasuredHeight();

        // 设置偏移量（让卡片出现在点的右上方）
        int offsetX = 40;
        int offsetY = -80;
        float finalX = rootX + offsetX;
        float finalY = rootY + offsetY;

        // 边界检查：防止超出屏幕右侧
        if (finalX + cardWidth > root.getWidth()) {
            finalX = rootX - cardWidth - 10;
        }
        // 边界检查：防止超出顶部
        if (finalY < 0) {
            finalY = rootY + 20;
        }

        // 设置卡片位置并添加到根布局
        tooltipView.setX(finalX);
        tooltipView.setY(finalY);
        root.addView(tooltipView);
    }

    //折线图
    private void loadChatTrendData(int days) {
        progressBar.setVisibility(View.VISIBLE);

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
                        progressBar.setVisibility(View.VISIBLE);
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
                        progressBar.setVisibility(View.GONE);
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
        xAxis.setGranularity(1f);//最小刻度间隔为1
        xAxis.setLabelCount(xLabels.size());//标签的数量
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

        lineChart.invalidate();

        //设置点击出现卡片
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index=(int)e.getX();
                if(index>=0 && index<xLabels.size()){
                    String date=xLabels.get(index);
                    int count=(int) e.getY();
                    showTooltip(h,date,count);
                }
            }
            @Override
            public void onNothingSelected() {
                hideTooltip();
            }
        });
        lineChart.invalidate();
    }

    //三个卡片
    private void showTooltip(Highlight h, String date, int count) {
        //先移除旧的卡片
        hideTooltip();
        //创建卡片视图
        if(tooltipView==null){
            tooltipView=getLayoutInflater().inflate(R.layout.tooltip_card,null);
            tooltipDate=tooltipView.findViewById(R.id.tv_date);
            tooltipCount = tooltipView.findViewById(R.id.tv_count);
        }
        if (tooltipDate == null) {
            Log.e(TAG, "tooltipDate is null! Check id 'tv_date' in tooltip_card.xml");
            return;
        }
        if (tooltipCount == null) {
            Log.e(TAG, "tooltipCount is null! Check id 'tv_count' in tooltip_card.xml");
            return;
        }

        // 🔥 关键修改：如果 date 是 "HH:00" 格式（包含冒号），则显示“昨日”或具体的年月日
        String displayDate = date;
        if (date != null && date.contains(":")) {
            // 获取昨天的日期（因为“昨日”数据通常指前一天）
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault());
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1);
            displayDate = sdf.format(calendar.getTime());  // 显示如 "04-28"
            // 如果你想显示完整的年-月-日，可以改为 "yyyy-MM-dd"
        }

        //填充数据
        tooltipDate.setText(date);
        tooltipCount.setText("人次：" + count);

        // 获取根布局（整个 Activity 的顶层视图）
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null) return;

        //计算数据点在屏幕上的绝对坐标
        float[] point = new float[]{h.getXPx(), h.getYPx()};
        int[] chartLocation = new int[2];
        lineChart.getLocationOnScreen(chartLocation);
        float rootX = chartLocation[0] + point[0];
        float rootY = chartLocation[1] + point[1];

        //测量卡片尺寸
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int cardWidth = tooltipView.getMeasuredWidth();
        int cardHeight = tooltipView.getMeasuredHeight();

        // 设置偏移量（让卡片出现在点的右上方）
        int offsetX = 40;
        int offsetY = -80;
        float finalX = rootX + offsetX;
        float finalY = rootY + offsetY;

        // 边界检查：防止超出屏幕右侧
        if (finalX + cardWidth > root.getWidth()) {
            finalX = rootX - cardWidth - 10;
        }
        // 边界检查：防止超出顶部
        if (finalY < 0) {
            finalY = rootY + 20;
        }

        // 设置卡片位置并添加到根布局
        tooltipView.setX(finalX);
        tooltipView.setY(finalY);
        root.addView(tooltipView);
    }

    private void hideTooltip() {
        if(tooltipView !=null && tooltipView.getParent()!=null){
            ((ViewGroup) tooltipView.getParent()).removeView(tooltipView);
        }
    }

    private void loadHotFaqData(int days){
        progressBar.setVisibility(View.VISIBLE);
        hotFaqBarChart.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

       AdminApiService adminApiService = RetrofitClient.getAdminApiService();
       Call<HotFaqResponse> call = adminApiService.getHotFaq(currentAttractionId, days);
       Log.d(TAG, "Request URL: " + call.request().url().toString());
       call.enqueue(new Callback<HotFaqResponse>() {
           @Override
           public void onResponse(Call<HotFaqResponse> call, Response<HotFaqResponse> response) {
               progressBar.setVisibility(View.GONE);
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
               progressBar.setVisibility(View.GONE);
               showEmptyState("网络错误：" + t.getMessage());
           }
       });
   }

   //展示空数据或错误信息
    private void showEmptyState(String message){
        hotFaqBarChart.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

   //展示柱状图
    private void showBarChart(List<HotFaqItem> dataList){
        progressBar.setVisibility(View.GONE);
        hotFaqBarChart.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        hotFaqBarChart.setData(dataList);
    }

    private void initView() {
        spinnerQa = findViewById(R.id.spinner_qa);
        progressBar = findViewById(R.id.progress_qa);
        tvEmpty = findViewById(R.id.tv_empty_qa);
        hotFaqBarChart = findViewById(R.id.hot_faq_bar_chart);
        tabTouristAnalysis = findViewById(R.id.tab_tourist_analysis);
        spinnerPeople=findViewById(R.id.spinner_people);
        tvTotalPeople = findViewById(R.id.tv_total_people);
        lineChart = findViewById(R.id.line_chart_people);
        tooltipDate=findViewById(R.id.tv_date);
        tooltipCount=findViewById(R.id.tv_count);
        ivTouristIcon=findViewById(R.id.iv_icon_tourist);
        tvTouristText=findViewById(R.id.tv_text_analysis);
        ivDataIcon=findViewById(R.id.iv_icon_data);
        tvDataText=findViewById(R.id.tv_text_data);
        tvBack=findViewById(R.id.tv_back);
        lineChartSatisfaction = findViewById(R.id.line_chart_satisfaction);

    }
}
