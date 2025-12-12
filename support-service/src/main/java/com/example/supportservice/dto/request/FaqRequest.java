package com.example.supportservice.dto.request;

import com.example.supportservice.entity.enumerate.FaqCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FaqRequest {
    private FaqCategory category;

    private String question;

    private String answer;
}