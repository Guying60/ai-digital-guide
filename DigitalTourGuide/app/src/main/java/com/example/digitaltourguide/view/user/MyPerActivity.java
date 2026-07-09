package com.example.digitaltourguide.view.user;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.digitaltourguide.R;
import com.example.digitaltourguide.model.BaseResponse;
import com.example.digitaltourguide.model.user.GuidePreference;
import com.example.digitaltourguide.model.user.GuidePreferenceRequest;
import com.example.digitaltourguide.network.ApiService;
import com.example.digitaltourguide.network.RetrofitClient;
import com.example.digitaltourguide.utils.SpUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.HashSet;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 导游偏好设置页面
 * 讲解风格 / 讲解深度 / 兴趣偏好 / 出游目的 / 特殊要求
 */
public class MyPerActivity extends AppCompatActivity {

    private static final String TAG = "MyPerActivity";

    // ── 状态字段 ──
    private String selectedStyle = null;
    private String selectedDepth = null;
    private final Set<String> selectedInterests = new HashSet<>();
    private String selectedPurpose = null;
    private String requirementsText = "";

    // ── 兴趣标签 ──
    private static final int[] TAG_CONTAINER_IDS = {
            R.id.tag_historical, R.id.tag_architecture, R.id.tag_royal,
            R.id.tag_nature, R.id.tag_photo, R.id.tag_myth,
            R.id.tag_folk, R.id.tag_food, R.id.tag_intangible
    };
    private static final int[] TAG_CHECK_IDS = {
            R.id.check_tag_historical, R.id.check_tag_architecture, R.id.check_tag_royal,
            R.id.check_tag_nature, R.id.check_tag_photo, R.id.check_tag_myth,
            R.id.check_tag_folk, R.id.check_tag_food, R.id.check_tag_intangible
    };
    private static final int[] TAG_TEXT_IDS = {
            R.id.tv_tag_historical, R.id.tv_tag_architecture, R.id.tv_tag_royal,
            R.id.tv_tag_nature, R.id.tv_tag_photo, R.id.tv_tag_myth,
            R.id.tv_tag_folk, R.id.tv_tag_food, R.id.tv_tag_intangible
    };
    private static final String[] TAG_LABELS = {
            "历史文化", "建筑艺术", "皇家故事",
            "自然生态", "摄影打卡", "神话传说",
            "民俗风情", "美食文化", "非遗文化"
    };
    // TAG_LABELS 对应的 API 枚举名（按相同下标一一对应）
    private static final String[] TAG_ENUM_NAMES = {
            "HISTORY_CULTURE", "ARCHITECTURE_ART", "ROYAL_STORIES",
            "NATURE_ECOLOGY", "PHOTOGRAPHY", "MYTHS_LEGENDS",
            "FOLK_CULTURE", "FOOD_CULTURE", "INTANGIBLE_HERITAGE"
    };
    private static final Set<String> DEFAULT_INTERESTS = new HashSet<>();

    // ── 卡片 ID 数组 ──
    private static final int[] STYLE_CARD_IDS = {
            R.id.card_style_professional, R.id.card_style_story,
            R.id.card_style_humorous, R.id.card_style_kids
    };
    private static final String[] STYLE_VALUES = {
            "professional", "story", "humorous", "kids"
    };
    private static final int[] STYLE_CHECK_IDS = {
            R.id.check_style_pro, R.id.check_style_story,
            R.id.check_style_humor, R.id.check_style_kids
    };

    private static final int[] DEPTH_CARD_IDS = {
            R.id.card_depth_brief, R.id.card_depth_standard, R.id.card_depth_deep
    };
    private static final String[] DEPTH_VALUES = {
            "brief", "standard", "deep"
    };
    private static final int[] DEPTH_CHECK_IDS = {
            R.id.check_depth_brief, R.id.check_depth_std, R.id.check_depth_deep
    };

    private static final int[] PURPOSE_CARD_IDS = {
            R.id.card_purpose_learning, R.id.card_purpose_family,
            R.id.card_purpose_photo, R.id.card_purpose_relax
    };
    private static final String[] PURPOSE_VALUES = {
            "learning", "family", "photo", "relax"
    };
    private static final int[] PURPOSE_CHECK_IDS = {
            R.id.check_purpose_learn, R.id.check_purpose_family,
            R.id.check_purpose_photo, R.id.check_purpose_relax
    };

    // ── 视图引用 ──
    private MaterialCardView[] styleCards = new MaterialCardView[4];
    private MaterialCardView[] depthCards = new MaterialCardView[3];
    private MaterialCardView[] purposeCards = new MaterialCardView[4];
    private TextView tvCharCount;
    private EditText etRequirements;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_per);

        SpUtils.init(this);
        apiService = RetrofitClient.getInstance().create(ApiService.class);

        initStyleCards();
        initDepthCards();
        initPurposeCards();
        initInterestTags();
        initRequirements();
        initToolbar();
        initSaveButton();

        loadPreferences();
    }

    // ═══════════════════════════════════════════════════════════════
    //  讲解风格 (单选项)
    // ═══════════════════════════════════════════════════════════════

    private void initStyleCards() {
        for (int i = 0; i < STYLE_CARD_IDS.length; i++) {
            MaterialCardView card = findViewById(STYLE_CARD_IDS[i]);
            styleCards[i] = card;
            final int index = i;
            card.setOnClickListener(v -> selectStyle(index));
        }
    }

    private void selectStyle(int index) {
        for (int i = 0; i < styleCards.length; i++) {
            setCardSelected(styleCards[i], i == index, STYLE_CHECK_IDS[i]);
        }
        selectedStyle = STYLE_VALUES[index];
    }

    private void clearStyleSelection() {
        for (int i = 0; i < styleCards.length; i++) {
            setCardSelected(styleCards[i], false, STYLE_CHECK_IDS[i]);
        }
        selectedStyle = null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  讲解深度 (单选项)
    // ═══════════════════════════════════════════════════════════════

    private void initDepthCards() {
        for (int i = 0; i < DEPTH_CARD_IDS.length; i++) {
            MaterialCardView card = findViewById(DEPTH_CARD_IDS[i]);
            depthCards[i] = card;
            final int index = i;
            card.setOnClickListener(v -> selectDepth(index));
        }
    }

    private void selectDepth(int index) {
        for (int i = 0; i < depthCards.length; i++) {
            setCardSelected(depthCards[i], i == index, DEPTH_CHECK_IDS[i]);
        }
        selectedDepth = DEPTH_VALUES[index];
    }

    private void clearDepthSelection() {
        for (int i = 0; i < depthCards.length; i++) {
            setCardSelected(depthCards[i], false, DEPTH_CHECK_IDS[i]);
        }
        selectedDepth = null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  兴趣偏好 (多选项)
    // ═══════════════════════════════════════════════════════════════

    private void initInterestTags() {
        selectedInterests.clear();

        for (int i = 0; i < TAG_CONTAINER_IDS.length; i++) {
            final String label = TAG_LABELS[i];
            final int containerId = TAG_CONTAINER_IDS[i];
            final int textId = TAG_TEXT_IDS[i];
            final int checkId = TAG_CHECK_IDS[i];

            View container = findViewById(containerId);
            TextView tvLabel = findViewById(textId);
            ImageView ivCheck = findViewById(checkId);

            // 初始状态
            boolean isSelected = selectedInterests.contains(label);
            updateTagUI(container, tvLabel, ivCheck, isSelected);

            container.setOnClickListener(v -> {
                boolean nowSelected = !selectedInterests.contains(label);
                if (nowSelected) {
                    selectedInterests.add(label);
                } else {
                    selectedInterests.remove(label);
                }
                updateTagUI(container, tvLabel, ivCheck, nowSelected);
            });
        }
    }

    private void updateTagUI(View container, TextView tvLabel, ImageView ivCheck, boolean selected) {
        tvLabel.setBackgroundResource(selected
                ? R.drawable.bg_chip_filter_selected
                : R.drawable.bg_chip_filter_unselected);
        tvLabel.setTextColor(getColor(selected
                ? R.color.profile_on_primary
                : R.color.profile_on_surface_variant));
        if (ivCheck != null) {
            ivCheck.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshInterestTagStates() {
        for (int i = 0; i < TAG_CONTAINER_IDS.length; i++) {
            View container = findViewById(TAG_CONTAINER_IDS[i]);
            TextView tvLabel = findViewById(TAG_TEXT_IDS[i]);
            ImageView ivCheck = findViewById(TAG_CHECK_IDS[i]);
            boolean isSelected = selectedInterests.contains(TAG_LABELS[i]);
            updateTagUI(container, tvLabel, ivCheck, isSelected);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  出游目的 (单选项)
    // ═══════════════════════════════════════════════════════════════

    private void initPurposeCards() {
        for (int i = 0; i < PURPOSE_CARD_IDS.length; i++) {
            MaterialCardView card = findViewById(PURPOSE_CARD_IDS[i]);
            purposeCards[i] = card;
            final int index = i;
            card.setOnClickListener(v -> selectPurpose(index));
        }
    }

    private void selectPurpose(int index) {
        for (int i = 0; i < purposeCards.length; i++) {
            setCardSelected(purposeCards[i], i == index, PURPOSE_CHECK_IDS[i]);
        }
        selectedPurpose = PURPOSE_VALUES[index];
    }

    private void clearPurposeSelection() {
        for (int i = 0; i < purposeCards.length; i++) {
            setCardSelected(purposeCards[i], false, PURPOSE_CHECK_IDS[i]);
        }
        selectedPurpose = null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  卡片选中/取消选中 UI
    // ═══════════════════════════════════════════════════════════════

    private void setCardSelected(MaterialCardView card, boolean selected, int checkMarkId) {
        FrameLayout checkMark = card.findViewById(checkMarkId);
        if (checkMark != null) {
            checkMark.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        if (selected) {
            card.setStrokeColor(getColor(R.color.profile_primary));
            card.setStrokeWidth(2);
            card.setCardBackgroundColor(getColor(R.color.profile_on_primary_container));
        } else {
            card.setStrokeColor(getColor(R.color.profile_outline_variant));
            card.setStrokeWidth(1);
            card.setCardBackgroundColor(getColor(R.color.profile_surface_container_lowest));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  特殊要求输入框
    // ═══════════════════════════════════════════════════════════════

    private void initRequirements() {
        etRequirements = findViewById(R.id.et_requirements);
        tvCharCount = findViewById(R.id.tv_char_count);

        etRequirements.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s.length();
                tvCharCount.setText(len + "/100");
                tvCharCount.setTextColor(getColor(len >= 100 ? R.color.profile_error : R.color.profile_outline));
                requirementsText = s.toString();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        int[] suggestionIds = {
                R.id.suggestion_1, R.id.suggestion_2,
                R.id.suggestion_4, R.id.suggestion_5
        };
        for (int id : suggestionIds) {
            TextView chip = findViewById(id);
            if (chip != null) {
                chip.setOnClickListener(v -> {
                    String text = chip.getText().toString();
                    String current = etRequirements.getText().toString();
                    String newText;
                    if (current.isEmpty()) {
                        newText = text;
                    } else if (current.endsWith("，") || current.endsWith("。") ||
                            current.endsWith(",") || current.endsWith(".")) {
                        newText = current + text;
                    } else {
                        newText = current + "，" + text;
                    }
                    etRequirements.setText(newText);
                    etRequirements.setSelection(newText.length());
                });
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Toolbar
    // ═══════════════════════════════════════════════════════════════

    private void initToolbar() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // "Reset" 按钮
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if ("Reset".equals(tv.getText().toString())) {
                    tv.setOnClickListener(v -> resetToDefaults());
                    break;
                }
            }
        }
    }

    private void initSaveButton() {
        findViewById(R.id.btn_save_preferences).setOnClickListener(v -> savePreferences());
    }

    // ═══════════════════════════════════════════════════════════════
    //  重置
    // ═══════════════════════════════════════════════════════════════

    private void resetToDefaults() {
        clearStyleSelection();
        clearDepthSelection();
        selectedInterests.clear();
        refreshInterestTagStates();
        clearPurposeSelection();
        etRequirements.setText("");
        Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  加载 / 保存
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    //  内部值 → API code 映射
    // ═══════════════════════════════════════════════════════════════

    private int styleToCode(String style) {
        if (style == null) return 1;
        switch (style) {
            case "professional": return 1;
            case "story":        return 2;
            case "humorous":     return 3;
            case "kids":         return 4;
            default:             return 1;
        }
    }

    private String codeToStyle(int code) {
        switch (code) {
            case 1:  return "professional";
            case 2:  return "story";
            case 3:  return "humorous";
            case 4:  return "kids";
            default: return "professional";
        }
    }

    private int depthToCode(String depth) {
        if (depth == null) return 2;
        switch (depth) {
            case "brief":    return 1;
            case "standard": return 2;
            case "deep":     return 3;
            default:         return 2;
        }
    }

    private String codeToDepth(int code) {
        switch (code) {
            case 1: return "brief";
            case 2: return "standard";
            case 3: return "deep";
            default: return "standard";
        }
    }

    private int purposeToCode(String purpose) {
        if (purpose == null) return 1;
        switch (purpose) {
            case "learning": return 1;
            case "family":   return 2;
            case "photo":    return 3;
            case "relax":    return 4;
            default:         return 1;
        }
    }

    private String codeToPurpose(int code) {
        switch (code) {
            case 1: return "learning";
            case 2: return "family";
            case 3: return "photo";
            case 4: return "relax";
            default: return "learning";
        }
    }

    /**
     * 中文兴趣标签 → API 枚举名（逗号分隔字符串）
     */
    private String interestsToApiEnum(Set<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TAG_LABELS.length; i++) {
            if (labels.contains(TAG_LABELS[i])) {
                if (sb.length() > 0) sb.append(",");
                sb.append(TAG_ENUM_NAMES[i]);
            }
        }
        return sb.toString();
    }

    /**
     * API 枚举名字符串（逗号分隔）→ 中文兴趣标签
     */
    private Set<String> apiEnumToInterests(String enumStr) {
        Set<String> result = new HashSet<>();
        if (enumStr == null || enumStr.isEmpty()) return result;
        String[] parts = enumStr.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            for (int i = 0; i < TAG_ENUM_NAMES.length; i++) {
                if (TAG_ENUM_NAMES[i].equals(trimmed)) {
                    result.add(TAG_LABELS[i]);
                    break;
                }
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  加载偏好（1.13 查询导览偏好）
    // ═══════════════════════════════════════════════════════════════

    private void loadPreferences() {
        apiService.getGuidePreference().enqueue(new Callback<BaseResponse<GuidePreference>>() {
            @Override
            public void onResponse(Call<BaseResponse<GuidePreference>> call,
                                   Response<BaseResponse<GuidePreference>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                BaseResponse<GuidePreference> resp = response.body();
                if (resp.getCode() == 1 && resp.getData() != null) {
                    applyPreferences(resp.getData());
                }
                // code != 1 或 data == null 说明用户尚未设置过偏好，保持默认
            }

            @Override
            public void onFailure(Call<BaseResponse<GuidePreference>> call, Throwable t) {
                Log.e(TAG, "加载偏好失败", t);
            }
        });
    }

    private void applyPreferences(GuidePreference pref) {
        // 讲解风格
        if (pref.getGuideStyle() != null) {
            String style = codeToStyle(pref.getGuideStyle());
            for (int i = 0; i < STYLE_VALUES.length; i++) {
                if (STYLE_VALUES[i].equals(style)) { selectStyle(i); break; }
            }
        }
        // 讲解深度
        if (pref.getGuideDepth() != null) {
            String depth = codeToDepth(pref.getGuideDepth());
            for (int i = 0; i < DEPTH_VALUES.length; i++) {
                if (DEPTH_VALUES[i].equals(depth)) { selectDepth(i); break; }
            }
        }
        // 兴趣偏好
        if (pref.getInterests() != null && !pref.getInterests().isEmpty()) {
            selectedInterests.clear();
            selectedInterests.addAll(apiEnumToInterests(pref.getInterests()));
            refreshInterestTagStates();
        }
        // 出游目的
        if (pref.getTravelPurpose() != null) {
            String purpose = codeToPurpose(pref.getTravelPurpose());
            for (int i = 0; i < PURPOSE_VALUES.length; i++) {
                if (PURPOSE_VALUES[i].equals(purpose)) { selectPurpose(i); break; }
            }
        }
        // 特殊需求
        if (pref.getSpecialRequirements() != null && !pref.getSpecialRequirements().isEmpty()) {
            etRequirements.setText(pref.getSpecialRequirements());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  保存偏好（1.12 保存或更新导览偏好）
    // ═══════════════════════════════════════════════════════════════

    private void savePreferences() {
        if (SpUtils.getUserToken(this) == null || SpUtils.getUserToken(this).isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        GuidePreferenceRequest request = buildGuidePreferenceRequest();
        findViewById(R.id.btn_save_preferences).setEnabled(false);

        apiService.saveGuidePreference(request)
                .enqueue(new Callback<BaseResponse<Void>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Void>> call, Response<BaseResponse<Void>> response) {
                        findViewById(R.id.btn_save_preferences).setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            BaseResponse<Void> resp = response.body();
                            if (resp.getCode() == 1) {
                                Toast.makeText(MyPerActivity.this, "偏好已保存", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(MyPerActivity.this, "保存失败：" + resp.getMsg(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(MyPerActivity.this, "保存失败 (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Void>> call, Throwable t) {
                        findViewById(R.id.btn_save_preferences).setEnabled(true);
                        Toast.makeText(MyPerActivity.this, "网络错误：" + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private GuidePreferenceRequest buildGuidePreferenceRequest() {
        GuidePreferenceRequest req = new GuidePreferenceRequest();
        req.setGuideStyle(styleToCode(selectedStyle));
        req.setGuideDepth(depthToCode(selectedDepth));
        req.setInterests(interestsToApiEnum(selectedInterests));
        req.setTravelPurpose(purposeToCode(selectedPurpose));
        req.setSpecialRequirements(requirementsText != null ? requirementsText : "");
        return req;
    }
}