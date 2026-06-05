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
}
