package com.example.digitaltourguide.view.admin;

import android.content.Intent;
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
import androidx.core.content.ContextCompat;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.admin.EmotionTrendData;
import com.example.digitaltourguide.model.admin.FocusCardData;
import com.example.digitaltourguide.model.admin.SuggestionData;
import com.example.digitaltourguide.network.AdminApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

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
    private EmotionMarkerView emotionMarkerView;
    private String currentAttractionId;
    private int currentDays = 7;
    private TextView tvPositiveRateMain, tvPositiveChange, tvTopFocus, tvTopFocusRate, tvWorstFocus;
    private CardView llSuggestion;
    private TextView tvSummary, tvSuggestion, tvScenicText, tvDataText;
    private Button btnScrollToSuggestion;
    private ScrollView scrollView;
    private LinearLayout tabDataAnalysis;
    private ImageView ivScenicIcon, ivDataIcon;

    // 图表色（从设计系统取）
    private int cPositive, cNeutral, cNegative, cMuted, cVariant, cOnSurface, cSurface, cContainer, cErrContainer, cSuccess, cPrimary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tourist_analysis);

        currentAttractionId = getIntent().getStringExtra("attraction_id");
        initColors();
        initViews();
        setupChartStyles();
        setupSpinner();
        loadEmotionTrendData(7);
        loadAiSuggestion();

        btnScrollToSuggestion.setOnClickListener(v -> {
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

    private void initColors() {
        cPositive     = ContextCompat.getColor(this, R.color.chart_positive);
        cNeutral      = ContextCompat.getColor(this, R.color.chart_line_primary);
        cNegative     = ContextCompat.getColor(this, R.color.chart_negative);
        cMuted        = ContextCompat.getColor(this, R.color.muted);
        cVariant      = ContextCompat.getColor(this, R.color.on_surface_variant);
        cOnSurface    = ContextCompat.getColor(this, R.color.on_surface);
        cSurface      = ContextCompat.getColor(this, R.color.surface);
        cContainer    = ContextCompat.getColor(this, R.color.primary_container);
        cErrContainer = ContextCompat.getColor(this, R.color.error_container);
        cSuccess      = ContextCompat.getColor(this, R.color.success);
        cPrimary      = ContextCompat.getColor(this, R.color.primary);
    }

    private void loadAiSuggestion() {
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
                    } catch (IOException e) { e.printStackTrace(); }
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
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        spinnerTimeCard.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                int days;
                switch (position) {
                    case 0: days = 1; break;
                    case 1: days = 7; break;
                    case 2: days = 30; break;
                    default: days = 7;
                }
                loadFocusCardData(days);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
        spinnerTimeCard.setSelection(1);
    }

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
        });
    }

    private void showCardErrorState(String s) {
        tvPositiveRateMain.setText("--%");
        tvPositiveChange.setText("暂无数据");
        tvTopFocus.setText("暂无数据");
        tvTopFocusRate.setText("");
        tvWorstFocus.setText("暂无数据");
    }

    private void updateFocusCards(FocusCardData data) {
        String topFocus = data.getTopFocus();
        String worstFocus = data.getWorstFocus();
        double positiveRate = data.getPositiveRate();

        boolean isEmpty = (topFocus == null || "暂无数据".equals(topFocus))
                && positiveRate == 0.0;

        if (isEmpty) {
            tvPositiveRateMain.setText("--%");
            tvPositiveRateMain.setTextColor(cMuted);
            tvPositiveChange.setText("暂无数据");
            tvPositiveChange.setTextColor(cMuted);
            tvTopFocus.setText("暂无数据");
            tvTopFocusRate.setText("");
            tvWorstFocus.setText("暂无数据");
            return;
        }

        tvPositiveRateMain.setText(String.format(Locale.CHINA, "%.1f%%", positiveRate));
        tvPositiveRateMain.setTextColor(cOnSurface);

        double change = data.getPositiveRateChange();
        String changeLabel = data.getChangeLabel() != null ? data.getChangeLabel() : "";
        String changeText;
        if (change > 0) {
            changeText = String.format(Locale.CHINA, "↑ %.1f%% %s", change, changeLabel);
            tvPositiveChange.setTextColor(cSuccess);
        } else if (change < 0) {
            changeText = String.format(Locale.CHINA, "↓ %.1f%% %s", Math.abs(change), changeLabel);
            tvPositiveChange.setTextColor(cOnSurface);
        } else {
            changeText = String.format(Locale.CHINA, "→ %.1f%% %s", change, changeLabel);
            tvPositiveChange.setTextColor(cOnSurface);
        }
        tvPositiveChange.setText(changeText);

        tvTopFocus.setText(topFocus != null ? topFocus : "暂无数据");
        Double topFocusRate = data.getTopFocusRate();
        if (topFocusRate != null) {
            tvTopFocusRate.setText(String.format(Locale.CHINA, "占所有问询 %.1f%%", topFocusRate));
        } else {
            tvTopFocusRate.setText("");
        }
        tvWorstFocus.setText(worstFocus != null ? worstFocus : "暂无数据");
    }

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
                        lineChart.clear();
                        lineChart.setNoDataText("暂无情感趋势数据");
                        lineChart.setNoDataTextColor(cMuted);
                        lineChart.invalidate();
                        pieChart.clear();
                        pieChart.setNoDataText("暂无情感数据");
                        pieChart.setNoDataTextColor(cMuted);
                        pieChart.invalidate();
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

        if (dates == null || dates.isEmpty()
                || positivesRates == null || neutralRates == null || negativeRates == null
                || positivesRates.isEmpty() || neutralRates.isEmpty() || negativeRates.isEmpty()) {
            lineChart.clear();
            lineChart.setNoDataText("暂无情感趋势数据");
            lineChart.setNoDataTextColor(cMuted);
            lineChart.invalidate();
            return;
        }

        emotionMarkerView.setData(dates, positivesRates, neutralRates, negativeRates);

        List<Entry> positiveEntries = new ArrayList<>();
        List<Entry> neutralEntries = new ArrayList<>();
        List<Entry> negativeEntries = new ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            positiveEntries.add(new Entry(i, positivesRates.get(i).floatValue()));
            neutralEntries.add(new Entry(i, neutralRates.get(i).floatValue()));
            negativeEntries.add(new Entry(i, negativeRates.get(i).floatValue()));
        }

        LineDataSet positiveSet = new LineDataSet(positiveEntries, "正面");
        styleDataSet(positiveSet, cPositive);
        LineDataSet neutralSet = new LineDataSet(neutralEntries, "中性");
        styleDataSet(neutralSet, cNeutral);
        LineDataSet negativeSet = new LineDataSet(negativeEntries, "负面");
        styleDataSet(negativeSet, cNegative);

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(positiveSet);
        dataSets.add(neutralSet);
        dataSets.add(negativeSet);

        LineData lineData = new LineData(dataSets);
        lineData.setDrawValues(false);
        lineChart.setData(lineData);

        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(dates));
        lineChart.getXAxis().setLabelCount(Math.min(dates.size(), 7), true);

        lineChart.animateX(800);
        lineChart.invalidate();
    }

    private void styleDataSet(LineDataSet set, int color) {
        set.setColor(color);
        set.setCircleColor(color);
        set.setCircleHoleColor(cSurface);
        set.setCircleRadius(5f);
        set.setCircleHoleRadius(3f);
        set.setLineWidth(2.5f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        set.setDrawValues(false);
        set.setDrawCircleHole(true);
        set.setHighlightEnabled(true);
        set.setHighLightColor(cOnSurface);
        set.setHighlightLineWidth(1.5f);
        set.enableDashedHighlightLine(8f, 4f, 0f);
        set.setDrawFilled(true);
        set.setFillColor(color);
        set.setFillAlpha(30);
    }

    private void updatePieChart(EmotionTrendData data) {
        double totalPositive = data.getTotalPositiveRate();
        double totalNeutral = data.getTotalNeutralRate();
        double totalNegative = data.getTotalNegativeRate();

        List<PieEntry> entries = new ArrayList<>();
        if (totalPositive > 0) entries.add(new PieEntry((float) totalPositive, "正面"));
        if (totalNeutral > 0) entries.add(new PieEntry((float) totalNeutral, "中性"));
        if (totalNegative > 0) entries.add(new PieEntry((float) totalNegative, "负面"));

        if (entries.isEmpty()) {
            pieChart.clear();
            pieChart.setNoDataText("暂无情感数据");
            pieChart.setNoDataTextColor(cMuted);
            pieChart.invalidate();
            return;
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[]{cPrimary, cContainer, cErrContainer});
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(cOnSurface);
        dataSet.setValueLinePart1Length(0.4f);
        dataSet.setValueLinePart2Length(0.4f);
        dataSet.setUsingSliceColorAsValueLineColor(true);
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(8f);

        PieData pieData = new PieData(dataSet);
        pieData.setValueFormatter(new com.github.mikephil.charting.formatter.PercentFormatter(pieChart));
        pieChart.setData(pieData);
        pieChart.setDrawEntryLabels(true);
        pieChart.setEntryLabelColor(cOnSurface);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.invalidate();
    }

    private void setupChartStyles() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDoubleTapToZoomEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setBackgroundColor(cSurface);
        lineChart.setViewPortOffsets(40f, 20f, 40f, 30f);
        lineChart.animateX(800);

        Legend legend = lineChart.getLegend();
        legend.setEnabled(true);
        legend.setTextSize(12f);
        legend.setTextColor(cVariant);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setFormSize(8f);
        legend.setFormToTextSpace(6f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setXEntrySpace(20f);
        legend.setYOffset(4f);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(0f);
        xAxis.setTextSize(11f);
        xAxis.setTextColor(cMuted);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(cContainer);
        xAxis.setAxisLineWidth(1f);
        xAxis.setYOffset(8f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(100f);
        leftAxis.setLabelCount(6, true);
        leftAxis.setTextSize(11f);
        leftAxis.setTextColor(cMuted);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(cContainer);
        leftAxis.setGridLineWidth(1f);
        leftAxis.enableGridDashedLine(8f, 4f, 0f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ((int) value) + "%";
            }
        });

        lineChart.getAxisRight().setEnabled(false);

        emotionMarkerView = new EmotionMarkerView(this, R.layout.marker_emotion);
        emotionMarkerView.setChartView(lineChart);
        lineChart.setMarker(emotionMarkerView);

        lineChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {}
            @Override
            public void onNothingSelected() {}
        });

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
        ivScenicIcon = findViewById(R.id.iv_icon_scenic);
        ivDataIcon = tabDataAnalysis.findViewById(R.id.iv_icon_data);
        tvScenicText = findViewById(R.id.tv_text_scenic);
        tvDataText = tabDataAnalysis.findViewById(R.id.tv_text_data);
    }
}
