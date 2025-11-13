package com.example.portfolioservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
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

    public PortfolioCardResponse(Long userId, Long portfolioId, String name, String email,
                                 String position, String photoUrl, String oneLiner,
                                 String hashtags, // DB에서 조회한 String 타입
                                 Long viewCount, Long likeCount,
                                 LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
        this.userId = userId;
        this.portfolioId = portfolioId;
        this.name = name;
        this.email = email;
        this.position = position;
        this.photoUrl = photoUrl;
        this.oneLiner = oneLiner;
        this.hashtags = stringToHashtagList(hashtags); // 생성자 내부에서 String -> List<String> 변환 수행
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }

    private List<String> stringToHashtagList(String hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return Collections.emptyList();
        }
        // DB에 쉼표(,)로 저장된 문자열을 분리
        return Arrays.asList(hashtags.split(","));
    }

}