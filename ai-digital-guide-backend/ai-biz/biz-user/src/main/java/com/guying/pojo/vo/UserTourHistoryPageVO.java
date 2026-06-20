package com.guying.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTourHistoryPageVO {
    private Long id;
    private String attractionName;
    private String coverUrl;
    private String conversationId;
    private String city;


}
