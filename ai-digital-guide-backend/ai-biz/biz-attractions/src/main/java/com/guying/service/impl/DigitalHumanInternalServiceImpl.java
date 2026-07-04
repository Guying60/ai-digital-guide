package com.guying.service.impl;

import com.guying.attractions.service.DigitalHumanInternalService;
import com.guying.pojo.vo.DigitalHumanVO;
import com.guying.service.AdminDigitalHumanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DigitalHumanInternalServiceImpl implements DigitalHumanInternalService {

    @Autowired
    private AdminDigitalHumanService adminDigitalHumanService;

    @Override
    public Long getDigitalHumanIdByAttractionId(Long attractionId) {
        DigitalHumanVO vo = adminDigitalHumanService.getByAttractionId(attractionId);
        return vo == null ? null : vo.getId();
    }
}
