package com.guying.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class SatisfactionTrendVO {
    private Double totalAvgScore;
    private List<String> dates;
    private List<Double> avgScores;
    private List<Integer> counts;
}
