package com.guying.converter;

import com.guying.ai.dto.EmotionTrendDTO;
import com.guying.ai.dto.SuggestionDTO;
import com.guying.pojo.vo.EmotionTrendVO;
import com.guying.pojo.vo.SuggestionVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnalysisConverter {
    SuggestionVO convert2SuggestionVO(SuggestionDTO suggestionDTO);

}
