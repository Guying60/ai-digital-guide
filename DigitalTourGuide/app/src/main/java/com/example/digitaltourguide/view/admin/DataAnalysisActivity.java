package com.example.digitaltourguide.view.admin;

import android.content.Intent;
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
import androidx.core.content.ContextCompat;

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
    private int currentDays = 1;
    private int satisfactionDays = 7;
    private LinearLayout tabTouristAnalysis;
    private TextView tvEmpty,tvTotalPeople,tvTouristText,tvDataText,tvBack;
    private DataTooltipMarkerView markerTrend, markerSatisfaction;
    private ImageView ivTouristIcon,ivDataIcon;
    private HotFaqBarChart hotFaqBarChart;
    private String currentAttractionId;

    // 图表色（从设计系统取）
    private int chartPrimary, chartSecondary, textMuted, textVariant, surfaceWhite, chartGrid;

    String token;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_analyse);

        initColors();
        initView();

        token=SpUtils.getAdminToken(this);
        Log.d(TAG,"token:"+token);

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

    /** 从设计系统色板初始化图表用色 */
    private void initColors() {
        chartPrimary  = ContextCompat.getColor(this, R.color.chart_line_primary);
        chartSecondary= ContextCompat.getColor(this, R.color.chart_line_secondary);
        textMuted     = ContextCompat.getColor(this, R.color.muted);
        textVariant   = ContextCompat.getColor(this, R.color.on_surface_variant);
        surfaceWhite  = ContextCompat.getColor(this, R.color.surface);
        chartGrid     = ContextCompat.getColor(this, R.color.outline);
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
                    case 0: satisfactionDays=7;break;
                    case 1: satisfactionDays=30;break;
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
        styleTrendDataSet(dataSet, chartSecondary);
        dataSet.setDrawValues(entries.size() <= 10);

        LineData lineData = new LineData(dataSet);
        lineChartSatisfaction.setData(lineData);

        applyChartStyle(lineChartSatisfaction, dates, true);

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
                                } catch (IOException e) { e.printStackTrace(); }
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

    private void drawLineChart(List<ChatTrendData.TrendItem> trendList) {
        lineChart.setVisibility(View.VISIBLE);

        if(trendList==null || trendList.isEmpty()){
            lineChart.setNoDataText("暂无数据");
            lineChart.invalidate();
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
        styleTrendDataSet(dataSet, chartPrimary);
        dataSet.setDrawValues(entries.size() <= 10);

        LineData lineData=new LineData(dataSet);
        lineChart.setData(lineData);

        applyChartStyle(lineChart, xLabels, false);

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
                       Log.e(TAG, "error body: " + response.errorBody().string());
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

    private void showEmptyState(String message){
        hotFaqBarChart.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message);
    }

    private void showBarChart(List<HotFaqItem> dataList){
        hotFaqBarChart.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        hotFaqBarChart.setData(dataList);
    }

    // ═══════════════════════════════════════════════════════════
    // 折线图样式
    // ═══════════════════════════════════════════════════════════

    private void styleTrendDataSet(LineDataSet set, int color) {
        set.setColor(color);
        set.setLineWidth(2.5f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);

        set.setCircleColor(surfaceWhite);
        set.setCircleHoleColor(color);
        set.setCircleRadius(4f);
        set.setCircleHoleRadius(3f);
        set.setDrawCircleHole(true);

        set.setDrawValues(true);
        set.setValueTextSize(10f);
        set.setValueTextColor(textVariant);

        set.setDrawFilled(true);
        set.setFillDrawable(makeGradientFill(color));

        set.setHighlightEnabled(true);
        set.setHighLightColor(textMuted);
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(10f, 5f, 0f);
    }

    private GradientDrawable makeGradientFill(int color) {
        int top = (color & 0x00FFFFFF) | 0x4D000000;
        int bottom = 0x00000000;
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    private void applyChartStyle(LineChart chart, List<String> xLabels, boolean isScore) {
        chart.getDescription().setEnabled(false);
        chart.setDrawBorders(false);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(surfaceWhite);
        chart.setNoDataTextColor(textMuted);

        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(false);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(8f);
        legend.setFormToTextSpace(6f);
        legend.setTextSize(12f);
        legend.setTextColor(textVariant);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setYOffset(4f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textMuted);
        xAxis.setTextSize(11f);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
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

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(textMuted);
        leftAxis.setTextSize(11f);
        leftAxis.setDrawGridLines(true);
        leftAxis.enableGridDashedLine(10f, 5f, 0f);
        leftAxis.setGridColor(chartGrid);
        leftAxis.setGridLineWidth(0.8f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setXOffset(8f);

        if (isScore) {
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

        chart.getAxisRight().setEnabled(false);
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
