package com.example.digitaltourguide.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.digitaltourguide.model.admin.HotFaqItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 热门问题 TOP 10 横向条形图 — 高级专业风格。
 *
 * 设计要点：
 * - 统一的翠绿色阶（排名越靠前颜色越深），和谐不刺眼
 * - 圆角条柱（5dp）+ 水平渐变
 * - 浅灰行分隔线，无厚重轴线
 * - 排名编号 + 问题文本（超长自动截断）+ 数值标签
 * - 点击任一行弹出 AlertDialog 查看完整问题，按下时有高亮反馈
 * - 底部数值刻度
 * - dp 单位自适应，避免硬编码 px
 */
public class HotFaqBarChart extends View {

    private List<HotFaqItem> dataList = new ArrayList<>();
    private float maxCount = 0f;

    // 画笔
    private Paint barPaint;
    private Paint textQuestion;
    private Paint textValue;
    private Paint textRank;
    private Paint lineGrid;
    private Paint highlightOverlay;  // 点击高亮半透明层

    // 触摸状态
    private int highlightIndex = -1;       // 当前按下的行，-1 表示无

    // 缓存的布局参数（onDraw 时更新，onTouchEvent 复用以定位点击行）
    private float cachedTopM, cachedRowH, cachedGap, cachedStartY;
    private int   cachedItemCount;

    // --------------- 翠绿色阶（排名 1→3 逐级变浅，其余最浅） ---------------
    private static final int[][] RANK_GRADIENT = {
        { 0xFF059669, 0xFF047857 },   // TOP 1  深翠绿 → 更深
        { 0xFF10B981, 0xFF059669 },   // TOP 2  标准翠绿 → 深
        { 0xFF34D399, 0xFF10B981 },   // TOP 3  浅翠绿 → 标准
    };
    private static final int FALLBACK_START = 0xFF6EE7B7;  // 4+ 最浅翠绿
    private static final int FALLBACK_END   = 0xFF34D399;

    public HotFaqBarChart(Context context) {
        super(context);
        init();
    }

    public HotFaqBarChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // --------------- dp 转换 ---------------
    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // --------------- 初始化 ---------------
    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);

        textQuestion = new Paint(Paint.ANTI_ALIAS_FLAG);
        textQuestion.setColor(Color.parseColor("#1F2937"));
        textQuestion.setTextSize(dp(13));

        textValue = new Paint(Paint.ANTI_ALIAS_FLAG);
        textValue.setColor(Color.parseColor("#374151"));
        textValue.setTextSize(dp(12));
        textValue.setFakeBoldText(true);

        textRank = new Paint(Paint.ANTI_ALIAS_FLAG);
        textRank.setColor(Color.parseColor("#9CA3AF"));
        textRank.setTextSize(dp(11));

        lineGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineGrid.setColor(Color.parseColor("#F3F4F6"));
        lineGrid.setStrokeWidth(dp(0.5f));

        highlightOverlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightOverlay.setStyle(Paint.Style.FILL);
        highlightOverlay.setColor(0x33FFFFFF);   // 20% 白色蒙层

        setClickable(true);
    }

    // --------------- 数据接口 ---------------
    public void setData(List<HotFaqItem> data) {
        this.dataList = data;
        maxCount = 0;
        for (HotFaqItem item : data) {
            if (item.getCount() > maxCount) maxCount = item.getCount();
        }
        if (maxCount == 0) maxCount = 1;
        highlightIndex = -1;
        requestLayout();
        invalidate();
    }

    // --------------- 绘制 ---------------
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataList == null || dataList.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float leftM  = dp(104);
        float rightM = dp(16);
        float topM   = dp(12);
        float botM   = dp(40);

        float chartW = w - leftM - rightM;
        float chartH = h - topM - botM;

        int n = dataList.size();
        // 每行最大高度不超过 44dp，防止只有一条数据时条柱过高占满整个卡片
        float maxRowH = dp(44);
        float rawRowH = chartH / n;
        float rowH   = Math.min(rawRowH, maxRowH);
        float gap    = rowH * 0.26f;
        float barH   = rowH - gap;
        float cornerR = dp(5);
        // 垂直居中：总内容高度 < chartH 时顶部留空
        float totalContentH = rowH * n;
        float y = topM + gap / 2f + (chartH - totalContentH) / 2f;

        // 缓存布局参数供 onTouchEvent 定位点击行
        cachedTopM      = topM;
        cachedRowH      = rowH;
        cachedGap       = gap;
        cachedStartY    = y;  // 垂直居中后的实际起始 Y
        cachedItemCount = n;

        for (int i = 0; i < n; i++) {
            HotFaqItem item = dataList.get(i);
            float barW = (item.getCount() / maxCount) * chartW;
            if (barW < dp(4)) barW = dp(4);

            float barT = y;
            float barR = leftM + barW;
            float barB = y + barH;

            // ---- 颜色：翠绿色阶，越靠前越深 ----
            int startColor, endColor;
            if (i < RANK_GRADIENT.length) {
                startColor = RANK_GRADIENT[i][0];
                endColor   = RANK_GRADIENT[i][1];
            } else {
                startColor = FALLBACK_START;
                endColor   = FALLBACK_END;
            }

            barPaint.setShader(new LinearGradient(leftM, 0, barR, 0,
                    startColor, endColor, Shader.TileMode.CLAMP));

            // 圆角条柱
            canvas.drawRoundRect(new RectF(leftM, barT, barR, barB), cornerR, cornerR, barPaint);

            // 点击高亮：白色半透明蒙层
            if (i == highlightIndex) {
                canvas.drawRoundRect(new RectF(leftM, barT, barR, barB), cornerR, cornerR, highlightOverlay);
            }

            // ---- 行分隔线 ----
            float sepY = y + rowH;
            if (i < n - 1) {
                canvas.drawLine(leftM, sepY, w - rightM, sepY, lineGrid);
            }

            float textBaseY = barT + barH / 2f + textQuestion.getTextSize() / 3f;

            // ---- 排名 ----
            String rank = String.valueOf(i + 1);
            float rankX = leftM - textRank.measureText(rank) - dp(10);
            canvas.drawText(rank, rankX, textBaseY, textRank);

            // ---- 问题文本（超长截断） ----
            String q = item.getQuestion();
            float maxQw = rankX - dp(8);
            String sq = clipText(q, textQuestion, maxQw);
            float qx = rankX - textQuestion.measureText(sq) - dp(6);
            canvas.drawText(sq, qx, textBaseY, textQuestion);

            // ---- 数值 ----
            String valStr = String.valueOf(item.getCount());
            canvas.drawText(valStr, barR + dp(8), textBaseY, textValue);

            y += rowH;
        }

        // ---- 底部刻度 ----
        int steps = 5;
        for (int i = 0; i <= steps; i++) {
            float x = leftM + (i * chartW / steps);
            String label = String.valueOf((int) (maxCount * i / steps));
            float lw = textRank.measureText(label);
            canvas.drawText(label, x - lw / 2f, h - dp(8), textRank);
        }
    }

    // --------------- 触摸：点击弹窗看完整问题 ---------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (dataList == null || dataList.isEmpty()) return super.onTouchEvent(event);

        int index = hitTest(event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (index >= 0) {
                    highlightIndex = index;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (index >= 0 && index == highlightIndex) {
                    showQuestionDialog(index);
                    performClick();
                }
                highlightIndex = -1;
                invalidate();
                return true;

            case MotionEvent.ACTION_CANCEL:
                highlightIndex = -1;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * 根据触摸 Y 坐标定位行索引，-1 表示未命中。
     */
    private int hitTest(float touchY) {
        float y = cachedStartY - cachedGap / 2f;
        for (int i = 0; i < cachedItemCount; i++) {
            if (touchY >= y && touchY < y + cachedRowH) {
                return i;
            }
            y += cachedRowH;
        }
        return -1;
    }

    /**
     * 弹出 AlertDialog 显示完整问题内容。
     */
    private void showQuestionDialog(int index) {
        String question = dataList.get(index).getQuestion();
        int rank = index + 1;
        int countVal = dataList.get(index).getCount();
        String title = "TOP " + rank + " · " + countVal + " 次";

        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(question)
                .setPositiveButton("关闭", null)
                .show();
    }

    // --------------- 文本截断 ---------------
    private String clipText(String text, Paint paint, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        for (int i = text.length() - 1; i > 0; i--) {
            String c = text.substring(0, i) + "…";   // …（省略号）
            if (paint.measureText(c) <= maxWidth) return c;
        }
        return "…";
    }
}