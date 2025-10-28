package com.example.userservice.dto;

import com.example.userservice.entity.UserEntity;
import com.example.userservice.entity.UserProvider;
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
    private String provider;    // provider (google, naver, kakao...)
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

    // UserEntity 생성 (가입 시)
    public UserEntity toEntity(String uniqueNickname) {
        return UserEntity.of(
                email,
                "SOCIAL_USER_PASSWORD_" + UUID.randomUUID(),
                uniqueNickname,
                UserProvider.valueOf(provider.toUpperCase()),
                providerId,
                this.name
        );
    }
}