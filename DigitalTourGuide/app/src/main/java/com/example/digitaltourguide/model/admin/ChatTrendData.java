package com.example.digitaltourguide.model.admin;

import java.util.List;

public class ChatTrendData {
    private Summary summary;
    private List<TrendItem> trendList;

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }
    public List<TrendItem> getTrendList() { return trendList; }
    public void setTrendList(List<TrendItem> trendList) { this.trendList = trendList; }

    public static class Summary {
        private int totalChats;
        public int getTotalChats() { return totalChats; }
        public void setTotalChats(int totalChats) { this.totalChats = totalChats; }
    }

    public static class TrendItem {
        private String time;
        private int count;
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
