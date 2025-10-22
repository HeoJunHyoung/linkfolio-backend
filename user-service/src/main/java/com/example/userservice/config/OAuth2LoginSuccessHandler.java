package com.example.userservice.config;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.UserDto;
import com.example.userservice.util.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.frontend.redirect-url}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. CustomOAuth2UserService에서 반환한 AuthUser 객체 추출
        AuthUser authUser = (AuthUser) authentication.getPrincipal();

        // 2. AuthUser로부터 UserDto 생성 (토큰 발급용)
        UserDto userDto = UserDto.of(
                authUser.getUserId(),
                authUser.getEmail(),
                null, // 소셜 로그인이므로 비밀번호 불필요
                null  // 닉네임 불필요 (토큰 생성 시 ID, Email만 사용)
        );

        // 3. JWT 토큰 생성
        String token = jwtTokenProvider.generateToken(userDto);
        log.info("OAuth2 Login Success. JWT Token generated for user: {}", authUser.getUserId());

        // 4. 프론트엔드 리디렉션 URL 생성 (토큰 포함)
        String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectUrl)
                .queryParam("token", token)
                .build()
                .toUriString();

        // 5. 프론트엔드로 리디렉션
        response.sendRedirect(targetUrl);
    }
}