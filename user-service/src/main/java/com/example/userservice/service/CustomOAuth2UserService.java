package com.example.userservice.service;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.OAuthAttributes;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.NicknameGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 기본 OAuth2 유저 정보 로드
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // 2. 공급자 식별 (google, naver, kakao)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 3. 공급자별 고유 ID (PK)
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        // 4. DTO로 변환 (공급자별 응답 파싱)
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        // 5. DB에서 사용자 조회 또는 신규 생성
        UserEntity userEntity = saveOrUpdate(attributes);

        // 6. AuthUser 객체 반환 (UserDetails 구현체)
        // 이 객체가 SecurityContext에 저장됨
        return new AuthUser(
                userEntity.getUserId(),
                userEntity.getEmail(),
                attributes.getAttributes(),
                attributes.getNameAttributeKey()
        );
    }

    private UserEntity saveOrUpdate(OAuthAttributes attributes) {
        // 이메일로 사용자 조회
        Optional<UserEntity> userOptional = userRepository.findUserDetailsByEmail(attributes.getEmail());

        UserEntity userEntity;
        if (userOptional.isPresent()) {
            // 이미 가입된 사용자인 경우 (업데이트 로직은 필요시 추가)
            userEntity = userOptional.get();
        } else {
            // 신규 사용자인 경우
            String nickname;
            do {
                nickname = NicknameGenerator.generate();
            } while (userRepository.existsByNickname(nickname)); // 닉네임 중복 검사

            userEntity = attributes.toEntity(nickname);
            userRepository.save(userEntity);
        }
        return userEntity;
    }
}