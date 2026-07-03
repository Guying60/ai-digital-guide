package com.guying.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 情感概览 VO：合并文本情感分析（正/中/负）与面部表情分析（7 类），
 * 外加文本来源的关注点，供管理后台在一个页面展示游客情感全貌。
 * 各 count/rate 数组下标与 dates 一一对齐；无数据日期 count 为 0、rate 为 0.0，日期序列不断档。
 */
@Data
public class EmotionOverviewVO {

    // ==================== 通用 ====================

    /** 日期序列，格式 MM-dd */
    private List<String> dates;

    // ==================== 文本情感（正/中/负，来自 tb_ai_experience_analysis） ====================

    private List<Integer> textPositiveCount;
    private List<Integer> textNeutralCount;
    private List<Integer> textNegativeCount;
    private List<Double>  textPositiveRate;
    private List<Double>  textNeutralRate;
    private List<Double>  textNegativeRate;
    /** 期内文本情感正面整体占比 */
    private Double textTotalPositiveRate;
    /** 期内文本情感中性整体占比 */
    private Double textTotalNeutralRate;
    /** 期内文本情感负面整体占比 */
    private Double textTotalNegativeRate;
    /** 期内文本分析记录总数 */
    private Integer textRecordCount;

    // ==================== 面部表情（7 类，来自 tb_face_emotion_record） ====================

    private List<Integer> faceJoyCount;
    private List<Integer> faceSurpriseCount;
    private List<Integer> faceNeutralCount;
    private List<Integer> faceConfusionCount;
    private List<Integer> faceDisgustCount;
    private List<Integer> faceAngerCount;
    private List<Integer> faceSadnessCount;
    private List<Double>  faceJoyRate;
    private List<Double>  faceSurpriseRate;
    private List<Double>  faceNeutralRate;
    private List<Double>  faceConfusionRate;
    private List<Double>  faceDisgustRate;
    private List<Double>  faceAngerRate;
    private List<Double>  faceSadnessRate;
    /** 期内面部表情喜悦整体占比 */
    private Double faceTotalJoyRate;
    /** 期内面部表情惊讶整体占比 */
    private Double faceTotalSurpriseRate;
    /** 期内面部表情中性整体占比 */
    private Double faceTotalNeutralRate;
    /** 期内面部表情困惑整体占比 */
    private Double faceTotalConfusionRate;
    /** 期内面部表情厌恶整体占比 */
    private Double faceTotalDisgustRate;
    /** 期内面部表情愤怒整体占比 */
    private Double faceTotalAngerRate;
    /** 期内面部表情悲伤整体占比 */
    private Double faceTotalSadnessRate;
    /** 期内面部表情记录总数 */
    private Integer faceRecordCount;

    // ==================== 关注点（文本来源） ====================

    /** 高频关注点 */
    private String topFocus;
    /** 待改善项 */
    private String worstFocus;
}
