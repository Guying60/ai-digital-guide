package com.guying.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarkDownDocumentMessage {
    private String ossUrl;
    private String fileName;
    private Long attractionId;
    private Long adminId;
    private String taskId;
    private String fileType;
    private String markdownText;
    private boolean success;
}
