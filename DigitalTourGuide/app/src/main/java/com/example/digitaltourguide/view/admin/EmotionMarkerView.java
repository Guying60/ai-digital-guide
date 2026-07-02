package com.example.digitaltourguide.view.admin;

import android.content.Context;
import android.widget.TextView;

import com.example.digitaltourguide.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;
import java.util.Locale;

/**
 * 情感趋势图的自定义 MarkerView，点击数据点时显示日期和三种情感占比。
 */
public class EmotionMarkerView extends MarkerView {

    private final TextView tvDate;
    private final TextView tvPositive;
    private final TextView tvNeutral;
    private final TextView tvNegative;
    private List<String> dates;
    private List<Double> positiveRates;
    private List<Double> neutralRates;
    private List<Double> negativeRates;

    public EmotionMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvDate = findViewById(R.id.tv_marker_date);
        tvPositive = findViewById(R.id.tv_marker_positive);
        tvNeutral = findViewById(R.id.tv_marker_neutral);
        tvNegative = findViewById(R.id.tv_marker_negative);
    }

    public void setData(List<String> dates,
                        List<Double> positiveRates,
                        List<Double> neutralRates,
                        List<Double> negativeRates) {
        this.dates = dates;
        this.positiveRates = positiveRates;
        this.neutralRates = neutralRates;
        this.negativeRates = negativeRates;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int index = (int) e.getX();
        if (dates != null && index >= 0 && index < dates.size()) {
            tvDate.setText(dates.get(index));
            double pos = positiveRates.get(index);
            double neu = neutralRates.get(index);
            double neg = negativeRates.get(index);
            tvPositive.setText(String.format(Locale.CHINA, "正面 %.1f%%", pos));
            tvNeutral.setText(String.format(Locale.CHINA, "中性 %.1f%%", neu));
            tvNegative.setText(String.format(Locale.CHINA, "负面 %.1f%%", neg));
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // 让 marker 底部中心对准数据点
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 10f);
    }
}
