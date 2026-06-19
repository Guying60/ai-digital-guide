package com.guying.pojo.vo;

import com.guying.common.enums.RouteStopStatus;
import lombok.Data;

/**
 * 路线数轴上的单个地标节点。
 * name/searchKeyword/recommendReason/estimatedMinutes 由 LLM 产出；
 * stopIndex/status 由后端赋值；longitude/latitude/address/poiId/resolved 由高德解析填充。
 */
@Data
public class RouteStopVO {

    /** 0 基序号，按地标顺序由后端赋值 */
    private int stopIndex;

    /** 地标展示名 */
    private String name;

    /** 更利于地图检索的具体地点名（供高德/前端定位用） */
    private String searchKeyword;

    /** 推荐理由（结合用户兴趣） */
    private String recommendReason;

    /** 建议游览时长（分钟） */
    private Integer estimatedMinutes;

    /** 经度（GCJ-02 火星坐标），未解析则为 null */
    private Double longitude;

    /** 纬度（GCJ-02 火星坐标），未解析则为 null */
    private Double latitude;

    /** 高德地址 */
    private String address;

    /** 高德 POI id */
    private String poiId;

    /** 是否成功解析到坐标；false 时前端需自行用地标名定位 */
    private boolean resolved;

    /** 节点状态 */
    private RouteStopStatus status;
}
