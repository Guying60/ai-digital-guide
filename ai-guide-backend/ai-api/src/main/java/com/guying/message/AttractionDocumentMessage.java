package com.guying.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttractionDocumentMessage {
    private String fileId;
    private String ossUrl;
    private String fileName;
    private String fileType;
    private Long attractionId;
    private Long adminId;
}
