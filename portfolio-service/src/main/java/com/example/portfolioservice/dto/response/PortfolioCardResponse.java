package com.example.portfolioservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioCardResponse {
    private Long userId; // 포트폴리오 작성자의 ID
    private Long portfolioId; // 포트폴리오 ID
    private String name;
    private String email;
    private String position;
    private String photoUrl;
    private String oneLiner;
    private List<String> hashtags;

    private Long viewCount;
    private Long likeCount;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}