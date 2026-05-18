package com.guying.pojo.vo;

import lombok.Data;

@Data
public class EmotionFocusCardVO {
    private Double positiveRate;      // 正面情感占比 86%
    private Double positiveRateChange; // 较上月变化 +21%
    private String changeLabel;

    private String topFocus;          // 高频关注点 "餐饮/票务"
    private Double topFocusRate;      // 占所有问询 71%

    private String worstFocus;   // 待改善项 "停车/导览"

}
