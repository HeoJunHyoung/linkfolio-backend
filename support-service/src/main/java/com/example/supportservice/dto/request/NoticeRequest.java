package com.example.supportservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoticeRequest {
    private String title;

    private String content;

    private boolean isImportant; // 중요(배지) 여부
}