package com.example.authservice.service.oauth;

import com.example.userservice.dto.OAuthAttributes;

import java.util.Map;

/**
 * 소셜 로그인 공급자(google, naver, kakao)별
 * 응답 Attribute를 파싱하는 전략(Strategy) 인터페이스
 */
public interface OAuth2AttributeParser {
    OAuthAttributes parse(String userNameAttributeName, Map<String, Object> attributes);
}
