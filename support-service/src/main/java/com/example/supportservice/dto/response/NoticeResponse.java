package com.example.supportservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class NoticeResponse {
    private Long id;
    private String title;
    private String content; // 목록 조회 시엔 요약본, 상세 시엔 전체
    private boolean isImportant;
    private LocalDate createdAt; // 날짜 포맷팅은 프론트 or JSON 설정으로 처리
}