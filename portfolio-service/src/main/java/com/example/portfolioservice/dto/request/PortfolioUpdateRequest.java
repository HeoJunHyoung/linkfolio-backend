package com.example.portfolioservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PortfolioUpdateRequest {
    // 프론트엔드의 '블럭 노트' 에디터가 생성한 JSON 문자열 혹은 text
    private String title;
    private String content;
}