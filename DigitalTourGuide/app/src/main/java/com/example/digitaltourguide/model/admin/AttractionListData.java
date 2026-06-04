package com.example.digitaltourguide.model.admin;

import java.util.List;

public class AttractionListData {
    private List<AddAttractionRequest> list;
    private String nextLastId;
    private boolean hasMore;
    public List<AddAttractionRequest> getList() { return list; }
    public String getNextLastId() { return nextLastId; }
    public boolean isHasMore() { return hasMore; }
}
