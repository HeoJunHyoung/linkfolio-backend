package com.example.userservice.handler;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.UserDto;
import com.example.userservice.service.RefreshTokenService;
import com.example.userservice.service.UserService;
import com.example.userservice.util.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    @Value("${app.frontend.redirect-url}")
    private String frontendRedirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        AuthUser authUser = (AuthUser) authentication.getPrincipal();

        // DB에서 최신 UserDto 조회 (토큰 생성용)
        UserDto userDto = userService.getUserDetailsById(authUser.getUserId());

        // Access Token 및 Refresh Token 생성
        String accessToken = jwtTokenProvider.generateAccessToken(userDto);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDto);

        // Refresh Token을 Redis에 저장
        refreshTokenService.saveRefreshToken(userDto.getId(), refreshToken);
        log.info("OAuth2 Login Success. JWT Tokens generated for user: {}", authUser.getUserId());

        // Refresh Token을 HttpOnly 쿠키에 담아 전송
        addRefreshTokenToCookie(response, refreshToken);

        // 프론트엔드 리디렉션 URL 생성 (Access Token만 파라미터로)
        String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectUrl)
                .queryParam("token", accessToken) // Access Token은 URL 파라미터로 전달
                .build()
                .toUriString();

        response.sendRedirect(targetUrl); // 리디렉션 응답
    }

    /**
     * Refresh Token을 HttpOnly 쿠키로 변환하는 헬퍼 메서드
     */
    private void addRefreshTokenToCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken);

        refreshTokenCookie.setHttpOnly(true);
        // refreshTokenCookie.setSecure(true); // TODO: HTTPS 환경에서는 true로 설정 필요
        refreshTokenCookie.setPath("/");

        int maxAgeInSeconds = (int) (jwtTokenProvider.getRefreshExpirationTimeMillis() / 1000);
        refreshTokenCookie.setMaxAge(maxAgeInSeconds);

        response.addCookie(refreshTokenCookie);
        log.debug("Refresh Token 쿠키 설정 완료.");
    }

}