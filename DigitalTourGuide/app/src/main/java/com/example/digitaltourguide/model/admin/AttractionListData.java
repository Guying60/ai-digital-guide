package com.example.digitaltourguide.model.admin;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AttractionListData {
    @SerializedName("list")
    private List<AddAttractionRequest> list;
    @SerializedName("nextLastId")
    private String nextLastId;
    @SerializedName("hasMore")
    private boolean hasMore;

    public List<AddAttractionRequest> getList() { return list; }
    public String getNextLastId() { return nextLastId; }
    public boolean isHasMore() { return hasMore; }
}