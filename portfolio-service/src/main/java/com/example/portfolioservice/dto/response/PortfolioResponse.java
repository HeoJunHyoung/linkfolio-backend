package com.example.portfolioservice.dto.response;

import com.example.portfolioservice.entity.enumerate.Gender;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PortfolioResponse {
    // 고정 정보 (캐시)
    private Long userId;
    private String name;
    private String email;
    private String birthdate;
    private Gender gender;
    // 사용자 입력 정보
    private String photoUrl;
    private String oneLiner;
    private String content;

    // UI 분기 처리를 위한 상태
    private boolean isPublished;
}