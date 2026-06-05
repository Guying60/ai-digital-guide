package com.guying.attractions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionDocumentDTO {



    private String ossUrl;

    private String fileName;
    private String fileType;

    private List<String> docIds;
    private Long attractionId;
    private Long adminId;
}
