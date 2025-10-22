package com.example.userservice.dto;

import com.example.userservice.entity.UserEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
public class OAuthAttributes {

    private Map<String, Object> attributes;
    private String nameAttributeKey;
    private String email;
    private String name;
    // provider (google, naver, kakao...)
    private String provider;
    // providerId (sub, id...)
    private String providerId;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes, String nameAttributeKey, String email, String name, String provider, String providerId) {
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
    }

    // registrationId (google, naver, kakao)에 따라 적절한 of...() 메서드 호출
    public static OAuthAttributes of(String registrationId, String userNameAttributeName, Map<String, Object> attributes) {
        if ("naver".equals(registrationId)) {
            return ofNaver("id", attributes);
        } else if ("kakao".equals(registrationId)) {
            return ofKakao("id", attributes);
        }
        return ofGoogle(userNameAttributeName, attributes);
    }

    private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .email((String) attributes.get("email"))
                .name((String) attributes.get("name"))
                .provider("google")
                .providerId((String) attributes.get("sub"))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuthAttributes ofNaver(String userNameAttributeName, Map<String, Object> attributes) {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        return OAuthAttributes.builder()
                .email((String) response.get("email"))
                .name((String) response.get("name"))
                .provider("naver")
                .providerId((String) response.get("id"))
                .attributes(response)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuthAttributes ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        return OAuthAttributes.builder()
                .email((String) kakaoAccount.get("email"))
                .name((String) profile.get("nickname"))
                .provider("kakao")
                .providerId(String.valueOf(attributes.get("id"))) // Long 타입이므로 String 변환
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    // UserEntity 생성 (가입 시)
    // 소셜 로그인은 비밀번호가 필요 없으므로 임의의 값(UUID)을 사용하거나 null 처리
    public UserEntity toEntity(String uniqueNickname) {
        return UserEntity.of(
                email,
                "SOCIAL_USER_PASSWORD_" + UUID.randomUUID(), // 임의의 비밀번호
                uniqueNickname
        );
    }
}