package com.example.digitaltourguide.view.user;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.digitaltourguide.model.user.RoutePlanVO;
import com.example.digitaltourguide.model.user.RouteStopVO;

/**
 * 胶囊数轴 — 展示 AI 推荐路线的有序地标节点，每个节点为胶囊形状。
 * 使用方法：{@link #setRoute(RoutePlanVO)} 渲染路线，{@link #clearRoute()} 清空。
 */
public class RouteTimelineView extends HorizontalScrollView {

    private static final int CAPSULE_HEIGHT_DP = 32;
    private static final int CONNECTOR_WIDTH_DP = 16;
    private static final int CONNECTOR_HEIGHT_DP = 3;
    private static final int INDICATOR_SIZE_DP = 20;

    private final LinearLayout container;
    private RoutePlanVO currentRoute;
    private OnGenerateClickListener generateListener;
    private OnCloseClickListener closeListener;
    private OnStopClickListener stopClickListener;

    // 状态色
    private static final int COLOR_ARRIVED = 0xFF22C55E;
    private static final int COLOR_CURRENT = 0xFFF59E0B;
    private static final int COLOR_UPCOMING = 0xFF94A3B8;
    private static final int COLOR_ARRIVED_TEXT = 0xFFFFFFFF;
    private static final int COLOR_CURRENT_TEXT = 0xFFFFFFFF;
    private static final int COLOR_UPCOMING_TEXT = 0xFF94A3B8;

    public RouteTimelineView(Context context) {
        this(context, null);
    }

    public RouteTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setPadding(dp(12), dp(6), dp(12), dp(6));

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        addView(container, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 默认显示生成按钮
        clearRoute();
    }

    // ====================== PUBLIC API ======================

    public void setOnGenerateClickListener(OnGenerateClickListener listener) {
        this.generateListener = listener;
    }

    public void setOnCloseClickListener(OnCloseClickListener listener) {
        this.closeListener = listener;
    }

    public void setOnStopClickListener(OnStopClickListener listener) {
        this.stopClickListener = listener;
    }

    /**
     * 根据 RoutePlanVO 渲染整个数轴（胶囊风格）
     */
    public void setRoute(RoutePlanVO route) {
        if (route == null || !route.hasStops()) {
            clearRoute();
            return;
        }
        this.currentRoute = route;
        container.removeAllViews();

        for (int i = 0; i < route.getStops().size(); i++) {
            RouteStopVO stop = route.getStops().get(i);
            // 节点之间的连接线（第一个节点前面不加）
            if (i > 0) {
                container.addView(createConnector(stop.isArrived()));
            }
            container.addView(createStopCapsule(stop));
        }

        // 右侧间距 + 关闭按钮 + 生成按钮
        container.addView(createSpacer(dp(8)));
        container.addView(createCloseButton());
        container.addView(createSpacer(dp(4)));
        container.addView(createGenerateButton());
    }

    /**
     * 清空数轴，仅显示生成按钮
     */
    public void clearRoute() {
        this.currentRoute = null;
        container.removeAllViews();
        container.addView(createGenerateButton());
    }

    public RoutePlanVO getCurrentRoute() {
        return currentRoute;
    }

    // ====================== 胶囊节点构建 ======================

    /**
     * 创建一个胶囊形状的站点节点
     */
    private View createStopCapsule(RouteStopVO stop) {
        LinearLayout capsule = new LinearLayout(getContext());
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setPadding(dp(4), dp(3), dp(12), dp(3));

        // 背景形状 — 高圆角矩形（胶囊效果）
        int bgColor, textColor, indicatorBg, indicatorText;
        String indicator;

        if (stop.isArrived()) {
            bgColor = COLOR_ARRIVED;
            textColor = COLOR_ARRIVED_TEXT;
            indicatorBg = 0xFFFFFFFF;
            indicatorText = COLOR_ARRIVED;
            indicator = "✓";
        } else if (stop.isCurrent()) {
            bgColor = COLOR_CURRENT;
            textColor = COLOR_CURRENT_TEXT;
            indicatorBg = 0xFFFFFFFF;
            indicatorText = COLOR_CURRENT;
            indicator = "▶";
        } else {
            bgColor = Color.TRANSPARENT;
            textColor = COLOR_UPCOMING_TEXT;
            indicatorBg = COLOR_UPCOMING;
            indicatorText = 0xFFFFFFFF;
            indicator = String.valueOf(stop.getStopIndex() + 1);
        }

        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.RECTANGLE);
        bgDrawable.setCornerRadius(dp(CAPSULE_HEIGHT_DP / 2));
        if (!stop.isUpcoming()) {
            bgDrawable.setColor(bgColor);
        } else {
            bgDrawable.setStroke(dp(1.5f), COLOR_UPCOMING);
            bgDrawable.setColor(0x1A94A3B8); // 微透明底
        }
        capsule.setBackground(bgDrawable);

        // ── 状态指示圆 ──
        TextView indicatorView = new TextView(getContext());
        indicatorView.setWidth(dp(INDICATOR_SIZE_DP));
        indicatorView.setHeight(dp(INDICATOR_SIZE_DP));
        indicatorView.setGravity(Gravity.CENTER);
        indicatorView.setText(indicator);
        indicatorView.setTextColor(indicatorText);
        indicatorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        indicatorView.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable indicatorBgDrawable = new GradientDrawable();
        indicatorBgDrawable.setShape(GradientDrawable.OVAL);
        indicatorBgDrawable.setColor(indicatorBg);
        indicatorView.setBackground(indicatorBgDrawable);

        // ── 站点名称 ──
        TextView nameView = new TextView(getContext());
        nameView.setText(stop.getName());
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        nameView.setTextColor(textColor);
        nameView.setGravity(Gravity.CENTER_VERTICAL);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setPadding(dp(6), 0, 0, 0);
        nameView.setTypeface(null, android.graphics.Typeface.MEDIUM);

        // ── 推荐时长（如果有） ──
        LinearLayout textContainer = new LinearLayout(getContext());
        textContainer.setOrientation(LinearLayout.HORIZONTAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        textContainer.addView(nameView);

        if (stop.getEstimatedMinutes() > 0) {
            TextView durationView = new TextView(getContext());
            durationView.setText(" · " + stop.getEstimatedMinutes() + "min");
            durationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            durationView.setTextColor(stop.isUpcoming()
                    ? 0xFF94A3B8
                    : 0xCCFFFFFF);
            durationView.setPadding(0, 0, 0, 0);
            textContainer.addView(durationView);
        }

        capsule.addView(indicatorView);
        capsule.addView(textContainer);

        // 点击事件
        capsule.setOnClickListener(v -> {
            if (stopClickListener != null) {
                stopClickListener.onStopClick(stop);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(CAPSULE_HEIGHT_DP + 4));
        params.gravity = Gravity.CENTER_VERTICAL;
        capsule.setLayoutParams(params);

        return capsule;
    }

    /**
     * 站点之间的连接线 — 胶囊风格圆角条
     */
    private View createConnector(boolean arrived) {
        View connector = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(CONNECTOR_WIDTH_DP), dp(CONNECTOR_HEIGHT_DP));
        params.gravity = Gravity.CENTER_VERTICAL;
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        connector.setLayoutParams(params);

        GradientDrawable line = new GradientDrawable();
        line.setShape(GradientDrawable.RECTANGLE);
        line.setCornerRadius(dp(CONNECTOR_HEIGHT_DP / 2));
        if (arrived) {
            line.setColor(COLOR_ARRIVED);
        } else {
            line.setColor(0x4064748B);
        }
        connector.setBackground(line);

        return connector;
    }

    private View createSpacer(int width) {
        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return spacer;
    }

    private Button createGenerateButton() {
        Button btn = new Button(getContext());
        btn.setText("AI路线");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(dp(16), dp(6), dp(16), dp(6));
        btn.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(0xFF3D3D3D);
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        params.gravity = Gravity.CENTER_VERTICAL;
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> {
            if (generateListener != null) {
                generateListener.onGenerateClick();
            }
        });
        return btn;
    }

    private Button createCloseButton() {
        Button btn = new Button(getContext());
        btn.setText("✕");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTextColor(0xFFEF4444);
        btn.setPadding(dp(8), dp(4), dp(8), dp(4));
        btn.setAllCaps(false);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(32), dp(32));
        params.gravity = Gravity.CENTER_VERTICAL;
        btn.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(0x0DEF4444);
        bg.setStroke(dp(1), 0x33EF4444);
        btn.setBackground(bg);

        btn.setOnClickListener(v -> {
            if (closeListener != null) {
                closeListener.onCloseClick();
            }
        });
        return btn;
    }

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    // ====================== 回调接口 ======================

    public interface OnGenerateClickListener {
        void onGenerateClick();
    }

    public interface OnCloseClickListener {
        void onCloseClick();
    }

    public interface OnStopClickListener {
        void onStopClick(RouteStopVO stop);
    }
}