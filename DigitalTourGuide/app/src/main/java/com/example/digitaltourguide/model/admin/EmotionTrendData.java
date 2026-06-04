package com.example.digitaltourguide.model.admin;

import java.util.List;

public class EmotionTrendData {
    private List<String> dates;
    private List<Integer> positiveCount;
    private List<Integer> neutralCount;
    private List<Integer> negativeCount;
    private List<Double> positiveRate;
    private List<Double> neutralRate;
    private List<Double> negativeRate;
    private double totalPositiveRate;
    private double totalNeutralRate;
    private double totalNegativeRate;

    public List<String> getDates() { return dates; }
    public void setDates(List<String> dates) { this.dates = dates; }
    public List<Integer> getPositiveCount() { return positiveCount; }
    public void setPositiveCount(List<Integer> positiveCount) { this.positiveCount = positiveCount; }
    public List<Integer> getNeutralCount() { return neutralCount; }
    public void setNeutralCount(List<Integer> neutralCount) { this.neutralCount = neutralCount; }
    public List<Integer> getNegativeCount() { return negativeCount; }
    public void setNegativeCount(List<Integer> negativeCount) { this.negativeCount = negativeCount; }
    public List<Double> getPositiveRate() { return positiveRate; }
    public void setPositiveRate(List<Double> positiveRate) { this.positiveRate = positiveRate; }
    public List<Double> getNeutralRate() { return neutralRate; }
    public void setNeutralRate(List<Double> neutralRate) { this.neutralRate = neutralRate; }
    public List<Double> getNegativeRate() { return negativeRate; }
    public void setNegativeRate(List<Double> negativeRate) { this.negativeRate = negativeRate; }
    public double getTotalPositiveRate() { return totalPositiveRate; }
    public void setTotalPositiveRate(double totalPositiveRate) { this.totalPositiveRate = totalPositiveRate; }
    public double getTotalNeutralRate() { return totalNeutralRate; }
    public void setTotalNeutralRate(double totalNeutralRate) { this.totalNeutralRate = totalNeutralRate; }
    public double getTotalNegativeRate() { return totalNegativeRate; }
    public void setTotalNegativeRate(double totalNegativeRate) { this.totalNegativeRate = totalNegativeRate; }
}
