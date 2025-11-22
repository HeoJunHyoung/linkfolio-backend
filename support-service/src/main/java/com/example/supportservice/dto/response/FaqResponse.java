package com.example.supportservice.dto.response;

import com.example.supportservice.entity.enumerate.FaqCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FaqResponse {
    private Long id;
    private FaqCategory category;
    private String question;
    private String answer;
}