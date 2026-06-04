package com.example.digitaltourguide.model.admin;

import java.util.List;

public class SatisfactionTrendVO {
    private double totalAvgScore;
    private List<String> dates;      // 格式 yyyy-MM-dd
    private List<Double> avgScores;  // 每日均分
    private List<Integer> counts;    // 每日评价数

    public double getTotalAvgScore() {
        return totalAvgScore;
    }

    public void setTotalAvgScore(double totalAvgScore) {
        this.totalAvgScore = totalAvgScore;
    }

    public List<String> getDates() {
        return dates;
    }

    public void setDates(List<String> dates) {
        this.dates = dates;
    }

    public List<Double> getAvgScores() {
        return avgScores;
    }

    public void setAvgScores(List<Double> avgScores) {
        this.avgScores = avgScores;
    }

    public List<Integer> getCounts() {
        return counts;
    }

    public void setCounts(List<Integer> counts) {
        this.counts = counts;
    }
}
