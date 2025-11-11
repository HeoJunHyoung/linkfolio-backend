package com.example.portfolioservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PortfolioRequest {
    private String photoUrl;
    private String oneLiner; // 한 마디 (PR)
    private String content;  // 내용 (자기소개)
    private String position;
    private List<String> hashtags;
}