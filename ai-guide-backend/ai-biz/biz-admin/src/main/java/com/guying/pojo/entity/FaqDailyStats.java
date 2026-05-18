package com.guying.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tb_faq_daily_stats")
public class FaqDailyStats {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long faqId;
    private Long attractionId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDate date;
    private Integer count;
}
