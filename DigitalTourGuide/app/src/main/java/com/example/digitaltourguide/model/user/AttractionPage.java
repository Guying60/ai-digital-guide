package com.example.digitaltourguide.model.user;

import java.util.List;

public class AttractionPage {
    private List<ScenicSpot> list;
    private String nextLastId;
    private Double nextDistance;
    private boolean hasMore;

    public List<ScenicSpot> getList() {
        return list;
    }

    public void setList(List<ScenicSpot> list) {
        this.list = list;
    }

    public String getNextLastId() {
        return nextLastId;
    }

    public void setNextLastId(String nextLastId) {
        this.nextLastId = nextLastId;
    }

    public Double getNextDistance() {
        return nextDistance;
    }

    public void setNextDistance(Double nextDistance) {
        this.nextDistance = nextDistance;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
