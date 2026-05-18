package com.guying.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DigitalHumanCreateDTO {
    @NotBlank(message = "数字人图片URL不能为空")
    private String ossUrl;
    @NotNull(message = "景区ID不能为空")
    private Long attractionId;
}
