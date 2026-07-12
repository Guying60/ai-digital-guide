package com.example.digitaltourguide.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.admin.HotFaqItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 热门问题统计图 — 兴趣分布风格。
 *
 * 设计要点：
 * - 问题文本在上方完整展示，下方为蓝色细线，长短代表问题数量
 * - 蓝色渐变（排名越靠前颜色越深），统一品牌感
 * - 灰色背景轨道辅助对比，数值标签在细线末端
 * - 排名编号 + 点击弹窗查看完整问题
 * - dp 单位自适应
 */
public class HotFaqBarChart extends View {

    private List<HotFaqItem> dataList = new ArrayList<>();
    private float maxCount = 0f;

    // 画笔
    private Paint linePaint;          // 蓝色比例细线
    private Paint bgTrackPaint;       // 灰色背景轨道
    private Paint textQuestion;       // 问题文本
    private Paint textValue;          // 次数数值
    private Paint textRank;           // 排名编号
    private Paint lineGrid;           // 行分隔线
    private Paint highlightOverlay;   // 点击高亮蒙层

    // 触摸状态
    private int highlightIndex = -1;

    // 缓存的布局参数
    private float dynamicLeftM, dynamicRightM;
    private float cachedStartY, cachedRowH;
    private int   cachedItemCount;

    // --------------- 蓝色色阶（TOP 1→3 由深到浅，其余统一浅蓝） ---------------
    private static final int[][] RANK_BLUE = {
        { 0xFF1E3A8A, 0xFF3B82F6 },   // TOP 1  藏蓝 → 亮蓝
        { 0xFF1D4ED8, 0xFF60A5FA },   // TOP 2  深蓝 → 中蓝
        { 0xFF2563EB, 0xFF93C5FD },   // TOP 3  标准蓝 → 浅蓝
    };
    private static final int FALLBACK_START = 0xFF3B82F6;   // 4+ 统一亮蓝
    private static final int FALLBACK_END   = 0xFF60A5FA;

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

    // --------------- 初始化画笔 ---------------
    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        Context ctx = getContext();

        bgTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgTrackPaint.setStyle(Paint.Style.FILL);
        bgTrackPaint.setColor(ContextCompat.getColor(ctx, R.color.surface_variant));

        textQuestion = new Paint(Paint.ANTI_ALIAS_FLAG);
        textQuestion.setColor(ContextCompat.getColor(ctx, R.color.on_surface));
        textQuestion.setTextSize(dp(13));

        textValue = new Paint(Paint.ANTI_ALIAS_FLAG);
        textValue.setColor(ContextCompat.getColor(ctx, R.color.primary));
        textValue.setTextSize(dp(12));
        textValue.setFakeBoldText(true);

        textRank = new Paint(Paint.ANTI_ALIAS_FLAG);
        textRank.setColor(ContextCompat.getColor(ctx, R.color.on_surface_variant));
        textRank.setTextSize(dp(12));
        textRank.setFakeBoldText(true);

        lineGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineGrid.setColor(ContextCompat.getColor(ctx, R.color.outline_variant));
        lineGrid.setStrokeWidth(dp(0.5f));

        highlightOverlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightOverlay.setStyle(Paint.Style.FILL);

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

    // --------------- 测量 ---------------
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (dataList == null || dataList.isEmpty()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int contentH = (int) (dp(14) + dp(48) * dataList.size() + dp(14));

        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            h = contentH;
        } else {
            h = Math.max(MeasureSpec.getSize(heightMeasureSpec), contentH);
        }

        setMeasuredDimension(w, h);
    }

    /** 根据数据动态计算左/右边距 */
    private void computeDynamicMargins() {
        float maxTextW = 0;
        float maxRankW = 0;
        for (int i = 0; i < dataList.size(); i++) {
            float tw = textQuestion.measureText(dataList.get(i).getQuestion());
            if (tw > maxTextW) maxTextW = tw;
            float rw = textRank.measureText(String.valueOf(i + 1));
            if (rw > maxRankW) maxRankW = rw;
        }
        float maxValW = textValue.measureText(String.valueOf((int) maxCount) + "次");
        // 左边距 = leftPad + rank + gap + questionText
        dynamicLeftM  = dp(16) + maxRankW + dp(8) + maxTextW;
        // 右边距 = 数值文本 + 缓冲
        dynamicRightM = dp(8) + maxValW + dp(16);
    }

    // --------------- 绘制 ---------------
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataList == null || dataList.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        computeDynamicMargins();

        float leftPad  = dp(16);
        float rightPad = dp(16);
        float topM     = dp(14);
        float botM     = dp(14);

        int n = dataList.size();

        // ---- 行布局参数 ----
        float rowH         = dp(48);            // 每行总高度
        float textTopOff   = dp(4);             // 文字顶部偏移
        float gapTextLine  = dp(6);             // 文字与细线间距
        float lineH        = dp(3);             // 细线高度
        float lineRadius   = dp(1.5f);          // 细线圆角

        // 排名宽度（取最大）
        float rankW = 0;
        for (int i = 0; i < n; i++) {
            float rw = textRank.measureText(String.valueOf(i + 1));
            if (rw > rankW) rankW = rw;
        }

        // 水平布局关键坐标
        float rankX       = leftPad;
        float questionX   = rankX + rankW + dp(8);        // 问题起始 X
        // 问题文本区域受限视图宽度，右侧预留数字空间
        float maxValW     = textValue.measureText(String.valueOf((int) maxCount) + "次");
        float valEndX     = w - rightPad;                  // 数字右对齐基线
        float lineEndX    = valEndX - maxValW - dp(12);    // 细线结束 X（数字左侧）
        float textEndX    = Math.min(dynamicLeftM, lineEndX - dp(60));
        float lineStartX  = questionX;                     // 细线起始 X（问题文本正下方）
        float maxLineW    = Math.max(dp(40), lineEndX - lineStartX); // 细线可用最大宽度

        // 垂直居中
        float totalH = rowH * n;
        float startY = topM + Math.max(0, (h - topM - botM - totalH) / 2f);

        // 缓存供触摸事件使用
        cachedStartY    = startY;
        cachedRowH      = rowH;
        cachedItemCount = n;

        for (int i = 0; i < n; i++) {
            HotFaqItem item = dataList.get(i);
            float rowTop = startY + i * rowH;

            // ========== 上半部分：排名 + 问题文本 ==========
            float textBaseY = rowTop + textTopOff + textQuestion.getTextSize();
            float rankBaseY = rowTop + textTopOff + textRank.getTextSize();

            // 排名
            String rank = String.valueOf(i + 1);
            canvas.drawText(rank, rankX, rankBaseY, textRank);

            // 问题文本（可能截断，点击弹窗看完整）
            float qMaxW = textEndX - questionX;
            String displayQ = clipText(item.getQuestion(), textQuestion, qMaxW);
            canvas.drawText(displayQ, questionX, textBaseY, textQuestion);

            // ========== 下半部分：细线 + 数值 ==========
            float lineTop = rowTop + textTopOff + textQuestion.getTextSize() + gapTextLine;

            // 背景轨道（浅灰，占满可用宽度）
            canvas.drawRoundRect(lineStartX, lineTop, lineEndX, lineTop + lineH,
                    lineRadius, lineRadius, bgTrackPaint);

            // 蓝色比例细线
            float lineW = (item.getCount() / maxCount) * maxLineW;
            if (lineW < dp(24)) lineW = dp(24);   // 最小值保证可见 + 能放下数字

            int startColor, endColor;
            if (i < RANK_BLUE.length) {
                startColor = RANK_BLUE[i][0];
                endColor   = RANK_BLUE[i][1];
            } else {
                startColor = FALLBACK_START;
                endColor   = FALLBACK_END;
            }

            linePaint.setShader(new LinearGradient(lineStartX, 0, lineStartX + lineW, 0,
                    startColor, endColor, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(lineStartX, lineTop, lineStartX + lineW, lineTop + lineH,
                    lineRadius, lineRadius, linePaint);

            // 数值标签（蓝色粗体，右侧固定竖线对齐）
            String valStr = item.getCount() + "次";
            float valW = textValue.measureText(valStr);
            float valX = valEndX - valW;
            float valY = lineTop + lineH / 2f + textValue.getTextSize() / 3f;
            canvas.drawText(valStr, valX, valY, textValue);

            // 点击高亮
            if (i == highlightIndex) {
                highlightOverlay.setColor(0x0D2563EB);   // 5% 蓝色蒙层
                canvas.drawRect(leftPad, rowTop, w - rightPad, rowTop + rowH, highlightOverlay);
            }

            // 行分隔线
            if (i < n - 1) {
                float sepY = rowTop + rowH;
                canvas.drawLine(leftPad, sepY, w - rightPad, sepY, lineGrid);
            }
        }

        // 清除 shader 避免后续绘制异常
        linePaint.setShader(null);
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

    /** 根据触摸 Y 坐标定位行索引 */
    private int hitTest(float touchY) {
        float y = cachedStartY;
        for (int i = 0; i < cachedItemCount; i++) {
            if (touchY >= y && touchY < y + cachedRowH) {
                return i;
            }
            y += cachedRowH;
        }
        return -1;
    }

    /** 弹出美化弹窗显示完整问题内容 */
    private void showQuestionDialog(int index) {
        String question = dataList.get(index).getQuestion();
        int rank = index + 1;
        int countVal = dataList.get(index).getCount();

        android.app.Dialog dialog = new android.app.Dialog(getContext());
        android.view.View view = android.view.LayoutInflater.from(getContext())
                .inflate(com.example.digitaltourguide.R.layout.dialog_faq_detail, null);

        android.widget.TextView tvRank = view.findViewById(com.example.digitaltourguide.R.id.tv_dialog_rank);
        android.widget.TextView tvCount = view.findViewById(com.example.digitaltourguide.R.id.tv_dialog_count);
        android.widget.TextView tvQuestion = view.findViewById(com.example.digitaltourguide.R.id.tv_dialog_question);
        android.widget.Button btnClose = view.findViewById(com.example.digitaltourguide.R.id.btn_dialog_close);

        tvRank.setText("TOP " + rank);
        tvCount.setText("被提问 " + countVal + " 次");
        tvQuestion.setText(question);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            // 固定弹窗宽度，保持统一
            int screenW = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout((int) (screenW * 0.8f), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    // --------------- 文本截断 ---------------
    private String clipText(String text, Paint paint, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        for (int i = text.length() - 1; i > 0; i--) {
            String c = text.substring(0, i) + "…";
            if (paint.measureText(c) <= maxWidth) return c;
        }
        return "…";
    }
}