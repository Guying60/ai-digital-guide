package com.guying.service;

import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.vo.DigitalHumanVO;
import org.springframework.core.io.Resource;

public interface AdminDigitalHumanService {
    DigitalHumanVO addOrUpdate(DigitalHumanCreateDTO dto);


    DigitalHumanVO getDetail(Long attractionId);

    /**
     * 游客侧查询：仅按 attractionId 查询数字人（不带 adminId），查不到返回 null。
     * 供数字人页待机循环播放原始视频使用。
     */
    DigitalHumanVO getByAttractionId(Long attractionId);

    String checkPreloadStatus(Long attractionId);

    String generateTestVideo(Long attractionId, String testText);

    String checkTestVideoStatus(Long attractionId);

    Resource proxyTestVideo(Long attractionId);

}
