package com.guying.attractions.service;

/**
 * 数字人跨模块查询接口，供 biz-ai WebSocket 等模块按景点解析数字人 ID。
 */
public interface DigitalHumanInternalService {

    /**
     * 按景点 ID 查询当前生效的数字人 ID（取最新一条），不存在时返回 null。
     */
    Long getDigitalHumanIdByAttractionId(Long attractionId);
}
