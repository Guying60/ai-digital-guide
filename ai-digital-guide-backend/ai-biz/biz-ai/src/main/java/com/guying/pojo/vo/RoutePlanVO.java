package com.guying.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 个性化路线推荐结果（灵动岛数轴）。
 * title/summary 由 LLM 产出；其余字段由后端补全。
 */
@Data
public class RoutePlanVO {

    /** 路线唯一 id（后端 UUID） */
    private String routeId;

    /** 所属会话 id */
    private String conversationId;

    /** 路线标题 */
    private String title;

    /** 路线总览说明 */
    private String summary;

    /** 所属景点 id */
    private Long attractionId;

    /** 高德锚点城市名（供前端定位限定城市，消除同名歧义） */
    private String cityName;

    /** 高德锚点 adcode */
    private String adcode;

    /** 景点中心点经度（GCJ-02），未解析则为 null */
    private Double centerLng;

    /** 景点中心点纬度（GCJ-02），未解析则为 null */
    private Double centerLat;

    /** 生成时间（epoch 毫秒） */
    private long generatedAt;

    /** 路线状态版本，每次到达更新递增 */
    private long revision;

    /** 有序地标节点 */
    private List<RouteStopVO> stops;
}
