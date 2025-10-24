package com.example.userservice.service;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.OAuthAttributes;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.entity.UserProvider;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.oauth.OAuth2AttributeParser; // [Refactor] Import
import com.example.userservice.util.NicknameGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map; // [Refactor] Import
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator; // (이전 리팩터링 적용됨)

    // 전략(Parser) Map 주입
    private final Map<String, OAuth2AttributeParser> attributeParsers;

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

        // 4. DTO로 변환 (전략 패턴 적용)
        // ㄴ Map에서 registrationId(예: "google")에 맞는 파서(전략)를 조회
        OAuth2AttributeParser parser = attributeParsers.get(registrationId);
        if (parser == null) {
            throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
        }

        // 해당 파서(전략)를 실행하여 속성을 DTO로 변환
        OAuthAttributes attributes = parser.parse(userNameAttributeName, oAuth2User.getAttributes());

        // 5. DB에서 사용자 조회 또는 신규 생성
        UserEntity userEntity = saveOrUpdate(attributes);

        // 6. AuthUser 객체 반환 (UserDetails 구현체)
        return AuthUser.fromOAuth2(
                userEntity,
                attributes.getAttributes(),
                attributes.getNameAttributeKey()
        );
    }

    private UserEntity saveOrUpdate(OAuthAttributes attributes) {
        UserProvider provider = UserProvider.valueOf(attributes.getProvider().toUpperCase());
        String providerId = attributes.getProviderId();
        String email = attributes.getEmail();

        // 1. (provider, providerId)로 소셜 유저 조회
        Optional<UserEntity> userOptional = userRepository.findByProviderAndProviderId(provider, providerId);

        if (userOptional.isPresent()) {
            // 1-1. 이미 해당 소셜 계정으로 가입된 경우
            return userOptional.get();
        }

        // 2. 해당 소셜 계정으로 가입 이력이 없는 경우, 이메일로 조회
        Optional<UserEntity> emailUserOptional = userRepository.findUserDetailsByEmail(email);

        if (emailUserOptional.isPresent()) {
            // 2-1. 이메일이 이미 존재하는데
            UserEntity existingUser = emailUserOptional.get();
            if (existingUser.getProvider() == UserProvider.LOCAL) {
                // 2-2. 로컬 계정으로 가입된 경우 -> 소셜 계정 연동 실패
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED_AS_LOCAL);
            } else {
                // 2-3. 다른 소셜 계정으로 가입된 경우 (e.g. 카카오로 가입했는데 구글 로그인 시도)
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED_AS_SOCIAL);
            }
        }

        // 3. 신규 가입
        String nickname = nicknameGenerator.generateUniqueNickname();
        UserEntity newUser = attributes.toEntity(nickname);
        return userRepository.save(newUser);
    }
}