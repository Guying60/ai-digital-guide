package com.guying.converter;

import com.guying.attractions.dto.AttractionDocumentDTO;
import com.guying.message.AttractionFqaMessage;
import com.guying.pojo.entity.Attraction;
import com.guying.pojo.entity.AttractionDocument;
import com.guying.pojo.entity.AttractionFaq;
import com.guying.pojo.vo.AttractionAdditionVO;
import com.guying.pojo.vo.DocumentsQueryVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdminAttractionsConverter {
    List<DocumentsQueryVO> toDocumentsQueryVOList(List<AttractionDocument> attractionDocuments);

    AttractionDocument toAttractionDocument(AttractionDocumentDTO attractionDocumentDTO);

    AttractionAdditionVO toAttractionAdditionVO(Attraction attraction);

    AttractionFaq toAttractionFaq(AttractionFqaMessage message);
}
