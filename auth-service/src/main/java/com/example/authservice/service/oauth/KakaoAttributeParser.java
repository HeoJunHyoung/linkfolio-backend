package com.example.authservice.service.oauth;

import com.example.userservice.dto.OAuthAttributes;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("kakao") // application.yml의 registrationId와 일치
public class KakaoAttributeParser implements OAuth2AttributeParser {

    @Override
    public OAuthAttributes parse(String userNameAttributeName, Map<String, Object> attributes) {
        // Kakao 응답은 "kakao_account" 내부에 이메일, "profile" 내부에 닉네임이 존재
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        return OAuthAttributes.builder()
                .email((String) kakaoAccount.get("email"))
                .name((String) profile.get("nickname"))
                .provider("kakao")
                .providerId(String.valueOf(attributes.get("id"))) // Kakao의 고유 ID (Long)
                .attributes(attributes)
                .nameAttributeKey("id") // Kakao는 "id"를 고유 키로 사용
                .build();
    }
}