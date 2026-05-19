package com.guying.service;

import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.vo.DigitalHumanVO;

public interface AdminDigitalHumanService {
    DigitalHumanVO addOrUpdate(DigitalHumanCreateDTO dto);

    void deleteById(Long id);

    DigitalHumanVO getDetail(Long attractionId);

}
