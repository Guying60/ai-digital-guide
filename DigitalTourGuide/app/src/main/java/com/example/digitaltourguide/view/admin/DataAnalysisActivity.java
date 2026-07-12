package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

        // 满意度均分折线——主色采用暖橙 #FF9800（与绿色服务人次图区分）
        LineDataSet dataSet = new LineDataSet(entries, "满意度均分");
        styleTrendDataSet(dataSet, Color.parseColor("#F59E0B"));

        LineData lineData = new LineData(dataSet);
        lineChartSatisfaction.setData(lineData);

        // 满意度范围为 0~5 分，第六个参数 isScore=true 切换 Y 轴刻度到 0~5
        applyChartStyle(lineChartSatisfaction, dates, true);

        // MarkerView
        markerSatisfaction = new DataTooltipMarkerView(this,
                dates.toArray(new String[0]), "满意度: ");
        List<Integer> countList = data.getCounts();
        int[] counts = new int[countList.size()];
        for (int i = 0; i < countList.size(); i++) counts[i] = countList.get(i);
        markerSatisfaction.setCounts(counts);
        markerSatisfaction.setChartView(lineChartSatisfaction);
        lineChartSatisfaction.setMarker(markerSatisfaction);
        lineChartSatisfaction.invalidate();
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

        // 服务人次折线——主色采用与 FAQ 区分的中性蓝绿 #10B981
        LineDataSet dataSet = new LineDataSet(entries, "服务人次");
        styleTrendDataSet(dataSet, Color.parseColor("#22C55E"));

        LineData lineData=new LineData(dataSet);
        lineChart.setData(lineData);

        // 坐标轴 / 网格 / 图例等通用样式
        applyChartStyle(lineChart, xLabels, false);

        // MarkerView 替代浮层
        markerTrend = new DataTooltipMarkerView(this,
                xLabels.toArray(new String[0]), "人次：");
        markerTrend.setChartView(lineChart);
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

    // ═══════════════════════════════════════════════════════════
    // 折线图高级样式工具方法（平滑曲线 / 渐变填充 / 虚线网格 / 图例）
    // ═══════════════════════════════════════════════════════════

    /**
     * 统一折线数据集样式：CUBIC_BEZIER 平滑曲线、白色描边实心圆点、
     * 30% 透明渐变填充、数值标签。
     *
     * @param set   LineDataSet 对象
     * @param color 线条主色（圆点内孔、填充均以此色衍生）
     */
    private void styleTrendDataSet(LineDataSet set, int color) {
        // ---- 线条 ----
        set.setColor(color);
        set.setLineWidth(2.5f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);   // 平滑贝塞尔曲线
        set.setCubicIntensity(0.2f);

        // ---- 数据点：白色描边的实心圆 ----
        // circleColor = 白色（外圈/描边），circleHoleColor = 线条色（内孔/实心）
        set.setCircleColor(Color.WHITE);
        set.setCircleHoleColor(color);
        set.setCircleRadius(4f);
        set.setCircleHoleRadius(3f);
        set.setDrawCircleHole(true);

        // ---- 数值标签 ----
        set.setDrawValues(true);
        set.setValueTextSize(10f);
        set.setValueTextColor(Color.parseColor("#64748B"));

        // ---- 渐变填充（自上而下：线条色 30% 透明 → 全透明） ----
        set.setDrawFilled(true);
        set.setFillDrawable(makeGradientFill(color));

        // ---- 点击高亮辅助线 ----
        set.setHighlightEnabled(true);
        set.setHighLightColor(Color.parseColor("#94A3B8"));
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(10f, 5f, 0f);
    }

    /**
     * 生成自上而下的渐变填充 Drawable（线条色 30% 不透明度 → 全透明）。
     */
    private GradientDrawable makeGradientFill(int color) {
        int top = (color & 0x00FFFFFF) | 0x4D000000;   // 0x4D ≈ 30% 不透明度
        int bottom = 0x00000000;                        // 完全透明
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    /**
     * 应用图表通用基础样式：去除边框、浅灰横向虚线网格、底部图例、
     * 双指缩放平移、X/Y 轴配色。
     *
     * @param chart   目标 LineChart
     * @param xLabels X 轴日期标签列表
     * @param isScore true 表示满意度图表（Y 轴 0~5），false 表示人次图表（自适应）
     */
    private void applyChartStyle(LineChart chart, List<String> xLabels, boolean isScore) {
        // ---- 基础设置 ----
        chart.getDescription().setEnabled(false);
        chart.setDrawBorders(false);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.WHITE);
        chart.setNoDataTextColor(Color.parseColor("#94A3B8"));

        // ---- 交互 ----
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);                   // 双指缩放
        chart.setDoubleTapToZoomEnabled(false);     // 双击缩放关闭，保留单指平移

        // ---- 图例：底部水平排列，圆形 ----
        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(8f);
        legend.setFormToTextSpace(6f);
        legend.setTextSize(12f);
        legend.setTextColor(Color.parseColor("#64748B"));
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(8f);

        // ---- X 轴：底部、次要灰色、无轴线/无竖向网格 ----
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#94A3B8"));
        xAxis.setTextSize(11f);
        xAxis.setDrawGridLines(false);              // 不画竖向网格线
        xAxis.setDrawAxisLine(false);               // 不画 X 轴线
        xAxis.setGranularity(1f);
        xAxis.setYOffset(6f);

        int size = xLabels.size();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));
        if (size > 15) {
            xAxis.setGranularity(5f);
            xAxis.setLabelCount(6, false);
        } else {
            xAxis.setGranularity(1f);
            xAxis.setLabelCount(size, false);
        }
        xAxis.setLabelRotationAngle(size > 6 ? 45f : 0f);

        // ---- 左 Y 轴：浅灰横向虚线网格，无轴线 ----
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#94A3B8"));
        leftAxis.setTextSize(11f);
        leftAxis.setDrawGridLines(true);
        leftAxis.enableGridDashedLine(10f, 5f, 0f);
        leftAxis.setGridColor(Color.parseColor("#2094A3B8"));   // 20% 透明度
        leftAxis.setGridLineWidth(0.8f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setXOffset(8f);

        if (isScore) {
            // 满意度 Y 轴：固定 0~5 分，6 个刻度
            leftAxis.setAxisMinimum(0f);
            leftAxis.setAxisMaximum(5f);
            leftAxis.setLabelCount(6, true);
            leftAxis.setGranularity(1f);
            leftAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.CHINA, "%.1f", value);
                }
            });
        } else {
            // 人次 Y 轴：自动范围，整数刻度
            leftAxis.setAxisMinimum(0f);
            leftAxis.setGranularity(1f);
            leftAxis.setLabelCount(6, false);
            leftAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.valueOf((int) value);
                }
            });
        }

        // ---- 右 Y 轴：隐藏 ----
        chart.getAxisRight().setEnabled(false);

        // ---- 留白 ----
        chart.setExtraOffsets(8f, 12f, 8f, 12f);
        chart.animateX(600);
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
