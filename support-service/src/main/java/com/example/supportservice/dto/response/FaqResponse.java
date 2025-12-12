package com.example.supportservice.dto.response;

import com.example.supportservice.entity.enumerate.FaqCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqResponse {
    private Long id;
    private FaqCategory category;
    private String question;
    private String answer;
}