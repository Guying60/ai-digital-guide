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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.user.RoutePlanVO;
import com.example.digitaltourguide.model.user.RouteStopVO;

/**
 * 胶囊数轴 — 展示 AI 推荐路线的有序地标节点，每个节点为胶囊形状。
 * 使用方法：{@link #setRoute(RoutePlanVO)} 渲染路线，{@link #clearRoute()} 清空。
 */
public class RouteTimelineView extends HorizontalScrollView {

    private static final int CAPSULE_HEIGHT_DP = 36;
    private static final int CONNECTOR_WIDTH_DP = 24;
    private static final int CONNECTOR_HEIGHT_DP = 2;
    private static final int INDICATOR_SIZE_DP = 12;

    private final LinearLayout container;
    private RoutePlanVO currentRoute;
    private boolean generating;
    private boolean expanded;
    private OnGenerateClickListener generateListener;
    private OnStopClickListener stopClickListener;

    private static final int COLOR_ROUTE_BACKGROUND = 0xE00F2850;
    private static final int COLOR_ARRIVED = 0xFF22C55E;
    private static final int COLOR_CURRENT = 0xFF3B82F6;
    private static final int COLOR_UPCOMING = 0x33FFFFFF;
    private static final int COLOR_ARRIVED_TEXT = 0xFF86EFAC;
    private static final int COLOR_CURRENT_TEXT = 0xFFFFFFFF;
    private static final int COLOR_UPCOMING_TEXT = 0x73FFFFFF;

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
        boolean routeChanged = currentRoute == null
                || !android.text.TextUtils.equals(currentRoute.getRouteId(), route.getRouteId());
        this.currentRoute = route;
        this.generating = false;
        if (routeChanged) {
            this.expanded = false;
        }
        renderRoute();
    }

    /**
     * 清空数轴，仅显示生成按钮
     */
    public void clearRoute() {
        this.currentRoute = null;
        this.generating = false;
        this.expanded = false;
        container.removeAllViews();
        setRouteBackground(false);
        container.addView(createGenerateButton());
    }

    public void showGenerating() {
        if (generating) {
            return;
        }
        generating = true;
        container.removeAllViews();
        setRouteBackground(false);
        container.addView(createGeneratingCapsule());
    }

    public void hideGenerating() {
        if (!generating) {
            return;
        }
        RoutePlanVO route = currentRoute;
        generating = false;
        if (route != null && route.hasStops()) {
            setRoute(route);
        } else {
            clearRoute();
        }
    }

    public RoutePlanVO getCurrentRoute() {
        return currentRoute;
    }

    private void renderRoute() {
        container.removeAllViews();
        setRouteBackground(true);
        if (!expanded) {
            container.addView(createCollapsedRoute());
            return;
        }

        for (int i = 0; i < currentRoute.getStops().size(); i++) {
            RouteStopVO stop = currentRoute.getStops().get(i);
            if (i > 0) {
                container.addView(createConnector(currentRoute.getStops().get(i - 1)));
            }
            container.addView(createStopCapsule(stop));
        }
        container.addView(createSpacer(dp(8)));
        container.addView(createCollapseButton());
    }

    private View createCollapsedRoute() {
        LinearLayout summary = new LinearLayout(getContext());
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(2), 0, dp(2), 0);

        FrameLayout status = new FrameLayout(getContext());
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor(0x593B82F6);
        status.setBackground(halo);
        View dot = new View(getContext());
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(0xFF60A5FA);
        dot.setBackground(dotBackground);
        status.addView(dot, new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER));
        summary.addView(status, new LinearLayout.LayoutParams(dp(20), dp(20)));

        int currentPosition = 0;
        RouteStopVO currentStop = currentRoute.getCurrentStop();
        for (int i = 0; i < currentRoute.getStops().size(); i++) {
            if (currentRoute.getStops().get(i).isCurrent()) {
                currentPosition = i;
                break;
            }
            if (currentRoute.getStops().get(i).isArrived()) {
                currentPosition = i;
            }
        }

        TextView progress = new TextView(getContext());
        progress.setText((currentPosition + 1) + "/" + currentRoute.getStops().size());
        progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        progress.setTextColor(0xB3FFFFFF);
        progress.setTypeface(null, android.graphics.Typeface.BOLD);
        progress.setPadding(dp(4), 0, dp(8), 0);
        summary.addView(progress);

        TextView name = new TextView(getContext());
        name.setText(currentStop != null ? currentStop.getName() : "路线已完成");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTextColor(Color.WHITE);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        summary.addView(name, new LinearLayout.LayoutParams(dp(88), dp(CAPSULE_HEIGHT_DP)));
        name.setGravity(Gravity.CENTER_VERTICAL);

        TextView arrow = new TextView(getContext());
        arrow.setText("▾");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        arrow.setTextColor(0xB3FFFFFF);
        arrow.setGravity(Gravity.CENTER);
        summary.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(CAPSULE_HEIGHT_DP)));

        summary.setOnClickListener(v -> {
            expanded = true;
            renderRoute();
        });
        return summary;
    }

    // ====================== 胶囊节点构建 ======================

    /**
     * 创建一个胶囊形状的站点节点
     */
    private View createStopCapsule(RouteStopVO stop) {
        LinearLayout stopView = new LinearLayout(getContext());
        stopView.setOrientation(LinearLayout.VERTICAL);
        stopView.setGravity(Gravity.CENTER_HORIZONTAL);
        stopView.setPadding(dp(4), 0, dp(4), 0);

        FrameLayout indicatorFrame = new FrameLayout(getContext());
        View indicator = new View(getContext());
        int textColor;
        int indicatorColor;
        int indicatorStroke;

        if (stop.isArrived()) {
            textColor = COLOR_ARRIVED_TEXT;
            indicatorColor = COLOR_ARRIVED;
            indicatorStroke = 0xFF86EFAC;
        } else if (stop.isCurrent()) {
            textColor = COLOR_CURRENT_TEXT;
            indicatorColor = COLOR_CURRENT;
            indicatorStroke = 0xFFBFDBFE;

            GradientDrawable halo = new GradientDrawable();
            halo.setShape(GradientDrawable.OVAL);
            halo.setColor(0x593B82F6);
            indicatorFrame.setBackground(halo);
        } else {
            textColor = COLOR_UPCOMING_TEXT;
            indicatorColor = COLOR_UPCOMING;
            indicatorStroke = 0x59FFFFFF;
        }

        GradientDrawable indicatorBackground = new GradientDrawable();
        indicatorBackground.setShape(GradientDrawable.OVAL);
        indicatorBackground.setColor(indicatorColor);
        indicatorBackground.setStroke(dp(2), indicatorStroke);
        indicator.setBackground(indicatorBackground);

        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                dp(INDICATOR_SIZE_DP), dp(INDICATOR_SIZE_DP), Gravity.CENTER);
        indicatorFrame.addView(indicator, indicatorParams);
        stopView.addView(indicatorFrame, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView nameView = new TextView(getContext());
        nameView.setText(stop.getName());
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        nameView.setTextColor(textColor);
        nameView.setGravity(Gravity.CENTER);
        nameView.setMaxLines(1);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        stopView.addView(nameView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(16)));

        stopView.setOnClickListener(v -> {
            if (stopClickListener != null) {
                stopClickListener.onStopClick(stop);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(CAPSULE_HEIGHT_DP));
        params.gravity = Gravity.CENTER_VERTICAL;
        stopView.setLayoutParams(params);
        return stopView;
    }

    /**
     * 站点之间的连接线 — 胶囊风格圆角条
     */
    private View createConnector(RouteStopVO previousStop) {
        FrameLayout connectorFrame = new FrameLayout(getContext());
        View connector = new View(getContext());

        GradientDrawable line = new GradientDrawable();
        line.setShape(GradientDrawable.RECTANGLE);
        line.setCornerRadius(dp(CONNECTOR_HEIGHT_DP / 2));
        if (previousStop.isArrived()) {
            line.setColor(0xFF4ADE80);
        } else if (previousStop.isCurrent()) {
            line.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            line.setColors(new int[]{COLOR_CURRENT, 0x2EFFFFFF});
        } else {
            line.setColor(0x2EFFFFFF);
        }
        connector.setBackground(line);

        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CONNECTOR_HEIGHT_DP));
        lineParams.topMargin = dp(9);
        connectorFrame.addView(connector, lineParams);

        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                dp(CONNECTOR_WIDTH_DP), dp(CAPSULE_HEIGHT_DP));
        frameParams.gravity = Gravity.CENTER_VERTICAL;
        connectorFrame.setLayoutParams(frameParams);
        return connectorFrame;
    }

    private View createSpacer(int width) {
        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return spacer;
    }

    private void setRouteBackground(boolean visible) {
        if (!visible) {
            container.setBackgroundColor(Color.TRANSPARENT);
            container.setPadding(0, 0, 0, 0);
            return;
        }
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(20));
        background.setColor(COLOR_ROUTE_BACKGROUND);
        background.setStroke(dp(1), 0x1FFFFFFF);
        container.setBackground(background);
        container.setPadding(dp(12), 0, dp(8), 0);
    }

    private View createGeneratingCapsule() {
        LinearLayout capsule = new LinearLayout(getContext());
        capsule.setOrientation(LinearLayout.HORIZONTAL);
        capsule.setGravity(Gravity.CENTER_VERTICAL);
        capsule.setPadding(dp(12), 0, dp(14), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(0x1A3B82F6);
        bg.setStroke(dp(1), 0x333B82F6);
        capsule.setBackground(bg);

        ProgressBar progressBar = new ProgressBar(getContext());
        progressBar.setIndeterminateDrawable(getResources().getDrawable(R.drawable.animation_loading_rotate));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        progressParams.gravity = Gravity.CENTER_VERTICAL;
        progressParams.rightMargin = dp(8);
        capsule.addView(progressBar, progressParams);

        TextView textView = new TextView(getContext());
        textView.setText("路线生成中…");
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTextColor(0xFF1E40AF);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        capsule.addView(textView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(CAPSULE_HEIGHT_DP));
        params.gravity = Gravity.CENTER_VERTICAL;
        capsule.setLayoutParams(params);
        return capsule;
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
            if (!generating && generateListener != null) {
                generateListener.onGenerateClick();
            }
        });
        return btn;
    }

    private Button createCollapseButton() {
        Button btn = new Button(getContext());
        btn.setText("✕");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTextColor(0xB3FFFFFF);
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
        bg.setColor(0x1FFFFFFF);
        bg.setStroke(dp(1), 0x1FFFFFFF);
        btn.setBackground(bg);

        btn.setOnClickListener(v -> {
            expanded = false;
            renderRoute();
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

    public interface OnStopClickListener {
        void onStopClick(RouteStopVO stop);
    }
}