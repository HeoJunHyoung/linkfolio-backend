package com.example.portfolioservice.dto.response;

import com.example.commonmodule.entity.enumerate.Gender;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PortfolioDetailsResponse {
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
    private String position;
    private List<String> hashtags;

    // UI 분기 처리를 위한 상태
    private boolean isPublished;
    private boolean isLiked;

    private Long viewCount;
    private Long likeCount;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}