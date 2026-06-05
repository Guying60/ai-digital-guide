package com.guying.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttractionFqaMessage {
    private Long id;
    private String question;
    private String answer;
    private Long attractionId;
}
