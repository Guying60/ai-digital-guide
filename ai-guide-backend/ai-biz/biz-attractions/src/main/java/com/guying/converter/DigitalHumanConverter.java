package com.guying.converter;

import com.guying.pojo.dto.DigitalHumanCreateDTO;
import com.guying.pojo.dto.DigitalHumanUpdateDTO;
import com.guying.pojo.entity.DigitalHuman;
import com.guying.pojo.vo.DigitalHumanVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DigitalHumanConverter {
    DigitalHuman toEntity(DigitalHumanCreateDTO dto);

    DigitalHuman toEntity(DigitalHumanUpdateDTO dto);

    DigitalHumanVO toVO(DigitalHuman entity);

    List<DigitalHumanVO> toVOList(List<DigitalHuman> entities);
}
