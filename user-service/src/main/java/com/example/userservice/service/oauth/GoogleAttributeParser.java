package com.example.userservice.service.oauth;

import com.example.userservice.dto.OAuthAttributes;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("google") // application.yml의 registrationId와 일치
public class GoogleAttributeParser implements OAuth2AttributeParser {

    @Override
    public OAuthAttributes parse(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .email((String) attributes.get("email"))
                .name((String) attributes.get("name"))
                .provider("google")
                .providerId((String) attributes.get("sub")) // Google의 고유 ID
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName) // "sub"
                .build();
    }
}