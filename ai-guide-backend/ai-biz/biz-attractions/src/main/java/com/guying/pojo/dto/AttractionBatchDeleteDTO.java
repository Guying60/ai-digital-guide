package com.guying.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionBatchDeleteDTO {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
