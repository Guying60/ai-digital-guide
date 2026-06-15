package com.example.digitaltourguide.model.user;

import java.util.List;

/**
 * 1.13 游标分页评价列表响应
 */
public class ReviewPage {
    private List<UserReviewItem> list;
    private String nextLastId;
    private boolean hasMore;

    public List<UserReviewItem> getList() { return list; }
    public void setList(List<UserReviewItem> list) { this.list = list; }

    public String getNextLastId() { return nextLastId; }
    public void setNextLastId(String nextLastId) { this.nextLastId = nextLastId; }

    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
}