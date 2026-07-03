package com.guying.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DigitalHumanCreateDTO {
    private Long id;
    @NotBlank(message = "数字人视频URL不能为空")
    private String videoUrl;
    @NotBlank(message = "数字人音频URL不能为空")
    private String audioUrl;
    @NotNull(message = "景区ID不能为空")
    private Long attractionId;
}
