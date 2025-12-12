package com.example.authservice.service.oauth;

import com.example.authservice.dto.OAuthAttributes;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("naver") // application.yml의 registrationId와 일치
public class NaverAttributeParser implements OAuth2AttributeParser {

    @Override
    public OAuthAttributes parse(String userNameAttributeName, Map<String, Object> attributes) {
        // Naver 응답은 "response" 키 내부에 실제 속성이 존재
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        return OAuthAttributes.builder()
                .email((String) response.get("email"))
                .name((String) response.get("name"))
                .provider("naver")
                .providerId((String) response.get("id")) // Naver의 고유 ID
                .attributes(response)
                .nameAttributeKey("id") // Naver는 "id"를 고유 키로 사용
                .build();
    }
}