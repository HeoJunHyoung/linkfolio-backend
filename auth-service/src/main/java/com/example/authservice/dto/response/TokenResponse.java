package com.example.authservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponse {
    // JSON 응답 본문에 포함될 필드
    private String accessToken;
}