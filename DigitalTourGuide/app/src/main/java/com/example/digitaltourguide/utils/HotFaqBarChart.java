package com.example.digitaltourguide.utils; // 改成您的包名

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.digitaltourguide.model.admin.HotFaqItem;

import java.util.ArrayList;
import java.util.List;

public class HotFaqBarChart extends View {

    private List<HotFaqItem> dataList = new ArrayList<>();
    private Paint barPaint;
    private Paint textPaint;
    private Paint axisPaint;
    private float maxCount = 0f;

    public HotFaqBarChart(Context context) {
        super(context);
        init();
    }

    public HotFaqBarChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#4CAF50"));
        barPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(28f); // 单位px，可根据屏幕适配

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(Color.GRAY);
        axisPaint.setStrokeWidth(2);
    }

    public void setData(List<HotFaqItem> data) {
        this.dataList = data;
        // 计算最大值
        maxCount = 0;
        for (HotFaqItem item : data) {
            if (item.getCount() > maxCount) {
                maxCount = item.getCount();
            }
        }
        if (maxCount == 0) maxCount = 1;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataList == null || dataList.isEmpty()) return;

        int width = getWidth();
        int height = getHeight();

        // 边距（单位px，可根据需要调整）
        int leftMargin = 160;   // 左侧留出问题文本空间
        int rightMargin = 60;   // 右侧留出数值轴和刻度
        int topMargin = 40;
        int bottomMargin = 40;

        int chartWidth = width - leftMargin - rightMargin;
        int chartHeight = height - topMargin - bottomMargin;

        // 绘制X轴（数值轴，在底部）
        canvas.drawLine(leftMargin, height - bottomMargin, width - rightMargin, height - bottomMargin, axisPaint);
        // 绘制Y轴（类别轴，在左侧）
        canvas.drawLine(leftMargin, topMargin, leftMargin, height - bottomMargin, axisPaint);

        int barCount = dataList.size();
        float barHeight = (float) chartHeight / barCount; // 每个条形的总高度（含间距）
        float barSpacing = barHeight * 0.7f;              // 条形之间的间距
        float actualBarHeight = barHeight - barSpacing;   // 条形实际绘制高度

        float currentY = topMargin + barSpacing / 2;

        // 绘制横向条形
        for (int i = 0; i < barCount; i++) {
            HotFaqItem item = dataList.get(i);
            float barWidth = (item.getCount() / maxCount) * chartWidth;

            // 绘制矩形
            float left = leftMargin;
            float top = currentY;
            float right = leftMargin + barWidth;
            float bottom = currentY + actualBarHeight;
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRect(rect, barPaint);

            // 绘制条形右侧的数值
            String countText = String.valueOf(item.getCount());
            float textWidth = textPaint.measureText(countText);
            canvas.drawText(countText, right + 8, top + actualBarHeight / 2 + 10, textPaint);

            // 绘制左侧的问题文本（截断处理）
            String question = item.getQuestion();
            String shortText = question.length() > 12 ? question.substring(0, 10) + ".." : question;
            // 文本垂直居中于条形
            float textY = top + actualBarHeight / 2 + 10;
            canvas.drawText(shortText, leftMargin - 120, textY, textPaint);

            currentY += barHeight;
        }

        // 绘制X轴刻度（数值轴底部）
        int xSteps = 5;
        for (int i = 0; i <= xSteps; i++) {
            float x = leftMargin + (i * chartWidth / xSteps);
            int value = (int) (maxCount * i / xSteps);
            // 绘制刻度标记
            canvas.drawLine(x, height - bottomMargin - 5, x, height - bottomMargin + 5, axisPaint);
            // 绘制数值文本
            canvas.drawText(String.valueOf(value), x - 20, height - bottomMargin + 30, textPaint);
        }
    }
}