package com.example.digitaltourguide.view.user;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.digitaltourguide.model.user.RoutePlanVO;
import com.example.digitaltourguide.model.user.RouteStopVO;

/**
 * 灵动岛数轴 — 展示 AI 推荐路线的有序地标节点。
 * 使用方法：{@link #setRoute(RoutePlanVO)} 渲染路线，{@link #clearRoute()} 清空。
 */
public class RouteTimelineView extends HorizontalScrollView {

    private static final int DOT_SIZE_DP = 14;
    private static final int LINE_WIDTH_DP = 2;
    private static final int LINE_LENGTH_DP = 32;
    private static final int LABEL_MAX_WIDTH_DP = 60;

    private final LinearLayout container;
    private RoutePlanVO currentRoute;
    private OnGenerateClickListener generateListener;
    private OnCloseClickListener closeListener;
    private OnStopClickListener stopClickListener;

    public RouteTimelineView(Context context) {
        this(context, null);
    }

    public RouteTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setPadding(dp(12), dp(8), dp(12), dp(8));

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
     * 根据 RoutePlanVO 渲染整个数轴
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
            // 节点之间的连线（第一个节点前面不加线）
            if (i > 0) {
                container.addView(createLine());
            }
            container.addView(createStopView(stop));
        }

        // 右侧间距 + 关闭按钮 + 生成按钮
        container.addView(createSpacer(dp(12)));
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

    // ====================== 内部构建方法 ======================

    private View createStopView(RouteStopVO stop) {
        LinearLayout item = new LinearLayout(getContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(dp(4), dp(4), dp(4), dp(4));

        // 状态色
        int dotColor;
        String statusText;
        if (stop.isArrived()) {
            dotColor = Color.parseColor("#22C55E");   // 绿色已到达
            statusText = "✓";
        } else if (stop.isCurrent()) {
            dotColor = Color.parseColor("#F59E0B");   // 橙色进行中
            statusText = "▶";
        } else {
            dotColor = Color.parseColor("#94A3B8");   // 灰色未到达
            statusText = "";
        }

        // 圆点（或状态图标）
        TextView dot = new TextView(getContext());
        dot.setWidth(dp(DOT_SIZE_DP));
        dot.setHeight(dp(DOT_SIZE_DP));
        dot.setGravity(Gravity.CENTER);
        dot.setText(statusText);
        dot.setTextColor(Color.WHITE);
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        dot.setBackground(createDotBackground(dotColor, stop.isUpcoming()));

        // 地标名称
        TextView label = new TextView(getContext());
        label.setText(stop.getName());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth(dp(LABEL_MAX_WIDTH_DP));
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);

        item.addView(dot);
        item.addView(label);

        // 点击事件
        item.setOnClickListener(v -> {
            if (stopClickListener != null) {
                stopClickListener.onStopClick(stop);
            }
        });

        return item;
    }

    private View createLine() {
        View line = new View(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(LINE_LENGTH_DP), dp(LINE_WIDTH_DP));
        params.gravity = Gravity.CENTER_VERTICAL;
        params.bottomMargin = dp(16); // 对齐圆点中心（偏移 label 高度）
        line.setLayoutParams(params);
        line.setBackgroundColor(Color.parseColor("#64748B"));
        return line;
    }

    private View createSpacer(int width) {
        View spacer = new View(getContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                width, 1));
        return spacer;
    }

    private Button createGenerateButton() {
        Button btn = new Button(getContext());
        btn.setText("AI路线");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(dp(16), dp(6), dp(16), dp(6));
        btn.setAllCaps(false);

        // 深灰色椭圆背景
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#3D3D3D"));
        btn.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32));
        params.gravity = Gravity.CENTER_VERTICAL;
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> {
            if (generateListener != null) {
                generateListener.onGenerateClick();
            }
        });
        return btn;
    }

    private android.graphics.drawable.Drawable createDotBackground(int color, boolean isOutline) {
        int sizePx = dp(DOT_SIZE_DP);
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        drawable.setSize(sizePx, sizePx);
        if (isOutline) {
            drawable.setStroke(dp(2), color);
            drawable.setColor(Color.TRANSPARENT);
        } else {
            drawable.setColor(color);
        }
        return drawable;
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    private Button createCloseButton() {
        Button btn = new Button(getContext());
        btn.setText("✕");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setTextColor(Color.parseColor("#EF4444"));
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setPadding(dp(8), dp(6), dp(8), dp(6));
        btn.setAllCaps(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> {
            if (closeListener != null) {
                closeListener.onCloseClick();
            }
        });
        return btn;
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
