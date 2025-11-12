package com.example.portfolioservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PortfolioCardResponse {
    private Long userId;
    private String name;
    private String email;
    private String position;
    private String photoUrl;
    private String oneLiner;
    private List<String> hashtags;

    private Long viewCount;
    private Long likeCount;
}