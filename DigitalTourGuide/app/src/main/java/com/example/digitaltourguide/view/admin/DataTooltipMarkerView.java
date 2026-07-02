package com.example.digitaltourguide.view.admin;

import android.content.Context;
import android.widget.TextView;

import com.example.digitaltourguide.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

/**
 * 数据分析页通用 tooltip MarkerView，显示两行文字。
 */
public class DataTooltipMarkerView extends MarkerView {

    private final TextView tvLine1;
    private final TextView tvLine2;

    private String[] labels;
    private String line2Prefix;
    private int[] counts; // 满意度图表的评价数，可为 null

    public DataTooltipMarkerView(Context context, String[] labels, String line2Prefix) {
        super(context, R.layout.marker_tooltip);
        tvLine1 = findViewById(R.id.tv_marker_label1);
        tvLine2 = findViewById(R.id.tv_marker_label2);
        this.labels = labels;
        this.line2Prefix = line2Prefix;
    }

    public void setLabels(String[] labels) { this.labels = labels; }
    public void setCounts(int[] counts) { this.counts = counts; }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int index = (int) e.getX();
        if (labels != null && index >= 0 && index < labels.length) {
            tvLine1.setText(labels[index]);
        }
        if (counts != null && index >= 0 && index < counts.length) {
            tvLine2.setText(String.format("%s%.1f 分 (评价数: %d)", line2Prefix, e.getY(), counts[index]));
        } else {
            tvLine2.setText(line2Prefix + e.getY());
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 12f);
    }
}