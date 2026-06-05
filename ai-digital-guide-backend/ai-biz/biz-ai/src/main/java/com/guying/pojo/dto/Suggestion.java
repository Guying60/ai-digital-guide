package com.guying.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Suggestion {
    private String summary;
    private String suggestion;
}
