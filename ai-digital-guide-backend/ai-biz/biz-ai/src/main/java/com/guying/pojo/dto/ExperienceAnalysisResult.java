package com.guying.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExperienceAnalysisResult {
    private String emotion;   // "正面" "中性" "负面"
    private List<String> focus; // ["餐饮", "停车"]
}