package com.guying.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScrollResult<T> {
    private List<T> list;
    private Long nextLastId;
    private Boolean hasMore;
    /**
     * 距离游标:下一页起始距离(米),仅"附近景点(按距离排序)"接口填充,其余接口为 null
     */
    private Double nextDistance;

    public ScrollResult(List<T> list, Long nextLastId, Boolean hasMore) {
        this.list = list;
        this.nextLastId = nextLastId;
        this.hasMore = hasMore;
    }
}
