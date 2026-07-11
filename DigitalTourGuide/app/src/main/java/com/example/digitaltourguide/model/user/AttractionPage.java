package com.example.digitaltourguide.model.user;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AttractionPage {
    @SerializedName("list")
    private List<ScenicSpot> list;
    @SerializedName("nextLastId")
    private String nextLastId;
    @SerializedName("nextDistance")
    private Double nextDistance;
    @SerializedName("hasMore")
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
