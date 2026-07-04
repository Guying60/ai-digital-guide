package com.guying.message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoPreloadMessage implements Serializable {
    private Long digitalHumanId;
    private String videoUrl;
    private String audioUrl;
}