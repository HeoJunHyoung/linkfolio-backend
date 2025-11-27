package com.example.supportservice.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class NoticeListResponse {
    private Long id;
    private String title;
    private boolean isImportant;
    private LocalDate createdAt;
}