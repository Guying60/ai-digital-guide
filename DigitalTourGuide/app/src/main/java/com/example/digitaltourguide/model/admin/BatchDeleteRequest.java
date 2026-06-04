package com.example.digitaltourguide.model.admin;

import java.util.List;

public class BatchDeleteRequest {
    private List<String> ids;   // 从 Long 改为 String

    public BatchDeleteRequest(List<String> ids) {
        this.ids = ids;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
