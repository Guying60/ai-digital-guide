package com.guying.attractions.service;

import com.guying.attractions.dto.AttractionDTO;

public interface UserAttractionsInternalService {
    AttractionDTO getAttraction(Long attractionId);


    String getAbsoluteFactByQuestionId(String faqId);
}
