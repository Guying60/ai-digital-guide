package com.guying.converter;

import com.guying.pojo.dto.AttractionCreateDTO;
import com.guying.pojo.dto.AttractionUpdateDTO;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionListVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AttractionsConverter {
    Attraction toAttraction(AttractionCreateDTO attractionCreateDTO);

    AttractionDetailVO toAttractionDetailVO(Attraction attraction);

    Attraction toAttraction(AttractionUpdateDTO attractionUpdateDTO);

    List<AttractionListVO> toAttractionListVOList(List<Attraction> attractionList);
}
