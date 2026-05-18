package com.guying.converter;

import com.guying.pojo.entity.Attraction;
import com.guying.pojo.vo.AttractionDetailVO;
import com.guying.pojo.vo.AttractionsAroundPageQueryVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserAttractionsConverter {

    List<AttractionsAroundPageQueryVO> toAttractionsAroundPageQueryVOList(List<Attraction> attractions);

    AttractionDetailVO toAttractionDetailVO(Attraction attraction);
}
