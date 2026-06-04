package com.example.digitaltourguide.model.admin;

public class FocusCardData {
    private double positiveRate;
    private double positiveRateChange;
    private String changeLabel;
    private String topFocus;
    private double topFocusRate;
    private String worstFocus;

    public double getPositiveRate() { return positiveRate; }
    public void setPositiveRate(double positiveRate) { this.positiveRate = positiveRate; }
    public double getPositiveRateChange() { return positiveRateChange; }
    public void setPositiveRateChange(double positiveRateChange) { this.positiveRateChange = positiveRateChange; }
    public String getChangeLabel() { return changeLabel; }
    public void setChangeLabel(String changeLabel) { this.changeLabel = changeLabel; }
    public String getTopFocus() { return topFocus; }
    public void setTopFocus(String topFocus) { this.topFocus = topFocus; }
    public double getTopFocusRate() { return topFocusRate; }
    public void setTopFocusRate(double topFocusRate) { this.topFocusRate = topFocusRate; }
    public String getWorstFocus() { return worstFocus; }
    public void setWorstFocus(String worstFocus) { this.worstFocus = worstFocus; }
}
