package com.guying.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VectorIngestMessage {
    private String ossUrl;
    private String fileName;
    private String fileType; // "pdf" | "doc" | "docx"
    private Long attractionId;
    private Long adminId;
    private String taskId;
}
