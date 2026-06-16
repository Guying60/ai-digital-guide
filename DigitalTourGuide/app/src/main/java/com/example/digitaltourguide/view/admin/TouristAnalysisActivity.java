package com.example.digitaltourguide.view.admin;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.EmotionTrendData;
import com.example.digitaltourguide.model.admin.FocusCardData;
import com.example.digitaltourguide.model.admin.SuggestionData;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TouristAnalysisActivity extends AppCompatActivity {
    private static final String TAG = "TouristAnalysisActivity";
    private Spinner spinnerTimeRange, spinnerTimeCard;
    private LineChart lineChart;
    private PieChart pieChart;
    private String currentAttractionId;
    private int currentDays = 7; // 默认近7天
    private TextView tvPositiveRateMain;   // 显示 86% 的位置
    private TextView tvPositiveChange;     // 显示 ↑21% 较上月 的位置
    private TextView tvTopFocus;           // 显示 餐饮/票务 的位置
    private TextView tvTopFocusRate;       // 显示 占所有问题71% 的位置
    private TextView tvWorstFocus;         // 显示 停车/导览 的位置
    private CardView llSuggestion;
    private TextView tvSummary, tvSuggestion, tvScenicText, tvDataText;
    private Button btnScrollToSuggestion;
    private ScrollView scrollView;
    private LinearLayout  tabDataAnalysis;
    private ImageView ivScenicIcon, ivDataIcon;
    private static final int ICON_SCENIC_SELECTED = R.drawable.ic_tourist_selected;  // 选中态
    private static final int ICON_SCENIC_NORMAL = R.drawable.ic_tourist_normal;    // 未选中态
    // 数据分析图标
    private static final int ICON_DATA_SELECTED = R.drawable.ic_data_selected;
    private static final int ICON_DATA_NORMAL = R.drawable.ic_data_normal;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tourist_analysis);

        currentAttractionId = getIntent().getStringExtra("attraction_id");

        initViews();
        setupChartStyles();
        setupSpinner();
        loadEmotionTrendData(7);
        loadAiSuggestion();  // 默认近7天

        btnScrollToSuggestion.setOnClickListener(v -> {
            // 滚动到服务建议区域
            scrollView.post(() -> scrollView.smoothScrollTo(0, llSuggestion.getTop()));
        });

        tabDataAnalysis.setOnClickListener(v -> {
            Intent intent = new Intent(TouristAnalysisActivity.this, DataAnalysisActivity.class);
            intent.putExtra("attraction_id", currentAttractionId);
            startActivity(intent);
            overridePendingTransition(R.anim.sibling_fade_in, R.anim.sibling_fade_out);
            finish();
        });
    }

    private void loadAiSuggestion() {
        // 默认加载近7天（type=0）
        loadAiSuggestionByType(0);
    }

    private void loadAiSuggestionByType(int type) {
        AdminApiService apiService = RetrofitClient.getAdminApiService();
        Call<BaseResponse<SuggestionData>> call = apiService.getAiServiceSuggestion(currentAttractionId, type);
        call.enqueue(new Callback<BaseResponse<SuggestionData>>() {
            @Override
            public void onResponse(Call<BaseResponse<SuggestionData>> call, Response<BaseResponse<SuggestionData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<SuggestionData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        SuggestionData data = baseResp.getData();
                        tvSummary.setText(data.getSummary() != null ? data.getSummary() : "暂无总结");
                        tvSuggestion.setText(data.getSuggestion() != null ? data.getSuggestion() : "暂无建议");
                    } else {
                        tvSummary.setText("暂无数据");
                        tvSuggestion.setText(baseResp.getMsg());
                    }
                } else {
                    try {
                        Log.e(TAG, "Error body: " + response.errorBody().string());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    tvSummary.setText("请求失败，HTTP " + response.code());
                    tvSuggestion.setText("请稍后重试");
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<SuggestionData>> call, Throwable t) {
                tvSummary.setText("网络错误");
                tvSuggestion.setText(t.getMessage());
            }
        });
    }

    private void setupSpinner() {
        spinnerTimeRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int days = (position == 0) ? 7 : 30;
                if (currentDays != days) {
                    currentDays = days;
                    loadEmotionTrendData(days);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        spinnerTimeCard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int days;
                switch (position) {
                    case 0:
                        days = 1;
                        break;   // 昨日
                    case 1:
                        days = 7;
                        break;   // 近7天
                    case 2:
                        days = 30;
                        break;  // 近30天
                    default:
                        days = 7;
                }
                loadFocusCardData(days);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        //默认选近七天
        spinnerTimeCard.setSelection(1);
    }

    //4.2加载卡片
    private void loadFocusCardData(int days) {
        AdminApiService apiService = RetrofitClient.getAdminApiService();
        Call<BaseResponse<FocusCardData>> call = apiService.getEmotionFocusCard(currentAttractionId, days);
        call.enqueue(new Callback<BaseResponse<FocusCardData>>() {
            @Override
            public void onResponse(Call<BaseResponse<FocusCardData>> call, Response<BaseResponse<FocusCardData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<FocusCardData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        updateFocusCards(baseResp.getData());
                    } else {
                        showCardErrorState(baseResp.getMsg() != null ? baseResp.getMsg() : "暂无数据");
                    }
                } else {
                    showCardErrorState("请求失败: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<FocusCardData>> call, Throwable t) {
                showCardErrorState("网络错误: " + t.getMessage());
            }

            private void showCardErrorState(String s) {
                tvPositiveRateMain.setText("--%");
                tvPositiveChange.setText("暂无数据");
                tvTopFocus.setText("暂无数据");
                tvTopFocusRate.setText("");
                tvWorstFocus.setText("暂无数据");
            }
        });
    }


    private void updateFocusCards(FocusCardData data) {
        //1.正面情感占比
        double positiveRate = data.getPositiveRate();
        tvPositiveRateMain.setText(String.format(Locale.CHINA, "%.1f%%", positiveRate));

        double change = data.getPositiveRateChange();
        String changeLabel = data.getChangeLabel() != null ? data.getChangeLabel() : "";
        String changeText = "";
        if (change > 0) {
            changeText = String.format(Locale.CHINA, "↑ %.1f%% %s", change, changeLabel);
            tvPositiveChange.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (change < 0) {
            changeText = String.format(Locale.CHINA, "↓ %.1f%% %s", Math.abs(change), changeLabel);
            tvPositiveChange.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            changeText = String.format(Locale.CHINA, "→ 0%% %s", changeLabel);
            tvPositiveChange.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
        tvPositiveChange.setText(changeText);

        //2.高频关注点
        String topFocus = data.getTopFocus();
        double topFocusRate = data.getTopFocusRate();
        tvTopFocus.setText(topFocus != null ? topFocus : "暂无数据");
        tvTopFocusRate.setText(String.format(Locale.CHINA, "占所有问询 %.1f%%", topFocusRate));

        // 3. 待改善
        String worstFocus = data.getWorstFocus();
        tvWorstFocus.setText(worstFocus != null ? worstFocus : "暂无数据");
    }

    //默认加载近七天数据
    private void loadEmotionTrendData(int days) {
        AdminApiService apiService = RetrofitClient.getAdminApiService();
        Call<BaseResponse<EmotionTrendData>> call = apiService.getEmotionTrend(currentAttractionId, days);
        call.enqueue(new Callback<BaseResponse<EmotionTrendData>>() {
            @Override
            public void onResponse(Call<BaseResponse<EmotionTrendData>> call, Response<BaseResponse<EmotionTrendData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BaseResponse<EmotionTrendData> baseResp = response.body();
                    if (baseResp.getCode() == 1 && baseResp.getData() != null) {
                        EmotionTrendData data = baseResp.getData();
                        updateLineChart(data);
                        updatePieChart(data);
                    } else {
                        Toast.makeText(TouristAnalysisActivity.this, baseResp.getMsg(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(TouristAnalysisActivity.this, "请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BaseResponse<EmotionTrendData>> call, Throwable t) {
                Toast.makeText(TouristAnalysisActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateLineChart(EmotionTrendData data) {
        List<String> dates = data.getDates();
        List<Double> positivesRates = data.getPositiveRate();
        List<Double> neutralRates = data.getNeutralRate();
        List<Double> negativeRates = data.getNegativeRate();

        if (dates == null || dates.isEmpty()) {
            lineChart.clear();
            lineChart.setNoDataText("暂无数据");
            lineChart.invalidate();
            return;
        }

        List<Entry> positiveEntries = new ArrayList<>();
        List<Entry> neutralEntries = new ArrayList<>();
        List<Entry> negativeEntries = new ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            positiveEntries.add(new Entry(i, positivesRates.get(i).floatValue()));
            neutralEntries.add(new Entry(i, neutralRates.get(i).floatValue()));
            negativeEntries.add(new Entry(i, negativeRates.get(i).floatValue()));
        }

        LineDataSet positiveSet = new LineDataSet(positiveEntries, "正面");
        positiveSet.setColor(getResources().getColor(android.R.color.holo_green_dark));
        positiveSet.setCircleColor(getResources().getColor(android.R.color.holo_green_dark));
        positiveSet.setLineWidth(2f);
        positiveSet.setCircleRadius(4f);
        positiveSet.setValueTextSize(10f);
        positiveSet.setDrawValues(true);

        LineDataSet neutralSet = new LineDataSet(neutralEntries, "中性");
        neutralSet.setColor(getResources().getColor(android.R.color.darker_gray));
        neutralSet.setCircleColor(getResources().getColor(android.R.color.darker_gray));
        neutralSet.setLineWidth(2f);
        neutralSet.setCircleRadius(4f);
        neutralSet.setValueTextSize(10f);
        neutralSet.setDrawValues(true);

        LineDataSet negativeSet = new LineDataSet(negativeEntries, "负面");
        negativeSet.setColor(getResources().getColor(android.R.color.holo_red_dark));
        negativeSet.setCircleColor(getResources().getColor(android.R.color.holo_red_dark));
        negativeSet.setLineWidth(2f);
        negativeSet.setCircleRadius(4f);
        negativeSet.setValueTextSize(10f);
        negativeSet.setDrawValues(true);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(positiveSet);
        dataSets.add(neutralSet);
        dataSets.add(negativeSet);

        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);
        // 设置X轴标签为日期
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));
        lineChart.getXAxis().setLabelCount(dates.size());
        // 点击折线图显示详情（弹出Toast，显示对应日期的positive/neutral/negative count）
        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                if (index >= 0 && index < dates.size()) {
                    int posCount = data.getPositiveCount().get(index);
                    int neuCount = data.getNeutralCount().get(index);
                    int negCount = data.getNegativeCount().get(index);
                    String msg = String.format(Locale.CHINA, "%s\n正面:%d 中性:%d 负面:%d",
                            dates.get(index), posCount, neuCount, negCount);
                    Toast.makeText(TouristAnalysisActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected() {
            }
        });

        lineChart.invalidate();
    }

    private void updatePieChart(EmotionTrendData data) {
        double totalPositive = data.getTotalPositiveRate();
        double totalNeutral = data.getTotalNeutralRate();
        double totalNegative = data.getTotalNegativeRate();

        List<PieEntry> entries = new ArrayList<>();
        if (totalPositive > 0) entries.add(new PieEntry((float) totalPositive, "正面"));
        if (totalNeutral > 0) entries.add(new PieEntry((float) totalNeutral, "中性"));
        if (totalNegative > 0) entries.add(new PieEntry((float) totalNegative, "负面"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        // 自定义紫色系配色
        int[] purpleColors = new int[]{
                Color.parseColor("#7C3AED"),  // 正面 - 紫色
                Color.parseColor("#A78BFA"),  // 中性 - 浅紫
                Color.parseColor("#FCA5A5"),  // 负面 - 浅红
        };
        dataSet.setColors(purpleColors);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.parseColor("#1F2937"));
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setUsingSliceColorAsValueLineColor(true);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new com.github.mikephil.charting.formatter.PercentFormatter(pieChart));
        pieChart.setData(pieData);
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(Color.parseColor("#1F2937"));
        pieChart.setEntryLabelTextSize(11f);
        pieChart.invalidate();
    }

    private void setupChartStyles() {
        // 折线图样式
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        lineChart.getLegend().setEnabled(true);
        lineChart.getLegend().setTextSize(12f);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-45f);
        xAxis.setTextSize(11f);

        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setAxisMaximum(100f);
        lineChart.getAxisRight().setEnabled(false);

        // 饼图样式
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(48f);
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelTextSize(12f);
    }

    private void initViews() {
        spinnerTimeRange = findViewById(R.id.spinner_time_range);
        lineChart = findViewById(R.id.line_chart_emotion);
        pieChart = findViewById(R.id.pie_chart_emotion);
        tvPositiveRateMain = findViewById(R.id.tv_positive_rate_main);
        tvPositiveChange = findViewById(R.id.tv_positive_change);
        tvTopFocus = findViewById(R.id.tv_top_focus);
        tvTopFocusRate = findViewById(R.id.tv_top_focus_rate);
        tvWorstFocus = findViewById(R.id.tv_worst_focus);
        spinnerTimeCard = findViewById(R.id.spinner_time_card);
        llSuggestion = findViewById(R.id.ll_suggestion);
        tvSummary = findViewById(R.id.tv_summary);
        tvSuggestion = findViewById(R.id.tv_suggestion);
        btnScrollToSuggestion = findViewById(R.id.btn_scroll_to_suggestion);
        scrollView = findViewById(R.id.scrollView);
        tabDataAnalysis = findViewById(R.id.tab_data_analysis);
        ivScenicIcon = findViewById(R.id.iv_icon_scenic);   // 需要给 ImageView 添加 id
        ivDataIcon = tabDataAnalysis.findViewById(R.id.iv_icon_data);
        tvScenicText = findViewById(R.id.tv_text_scenic);   // 给 TextView 添加 id
        tvDataText = tabDataAnalysis.findViewById(R.id.tv_text_data);

    }

}
