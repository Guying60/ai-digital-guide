package com.guying.pojo.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatTrendVO {

    private Summary summary;

    private List<Trend> trendList;



    @Data
    public static class Summary {
        private Integer totalChats;
    }

    @Data
    public static class Trend {
        private String time;  // 时间节点
        private Integer count; // 数量
    }

}
