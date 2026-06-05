package com.guying.attractions.service;

import com.guying.attractions.dto.AttractionDocumentDTO;

import java.util.List;

public interface AdminAttractionsInternalService {
    void saveDocumentToMySql(AttractionDocumentDTO attractionDocumentDTO);

    List<Long> getAttractionIdList();


}
