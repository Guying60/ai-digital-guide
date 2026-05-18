package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value ="tb_attraction_document", autoResultMap = true)
public class AttractionDocument {


    @TableId(type = IdType.AUTO)
    private Long id;


    private String ossUrl;

    private String fileName;
    private String fileType;

    @TableField(value = "doc_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> docIds;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long attractionId;
    private Long adminId;
}
