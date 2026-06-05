package com.guying.service;

import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.vo.DigitalHumanVO;
import org.springframework.core.io.Resource;

public interface AdminDigitalHumanService {
    DigitalHumanVO addOrUpdate(DigitalHumanCreateDTO dto);


    DigitalHumanVO getDetail(Long attractionId);

    String checkPreloadStatus(Long attractionId);

    String generateTestVideo(Long attractionId, String testText);

    String checkTestVideoStatus(Long attractionId);

    Resource proxyTestVideo(Long attractionId);

}
