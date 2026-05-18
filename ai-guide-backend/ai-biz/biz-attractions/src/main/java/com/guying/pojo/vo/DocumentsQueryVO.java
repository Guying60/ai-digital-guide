package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentsQueryVO {

    private Long id;
    private String ossUrl;
    private String fileName;
    private String fileType;

}
