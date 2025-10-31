package com.example.authservice.handler;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.response.TokenResponse;
import com.example.userservice.service.RefreshTokenService;
import com.example.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로컬 로그인(JSON) 성공 시 JWT를 생성하여 응답 헤더에 추가하는 핸들러.
 * CustomAuthenticationFilter의 successfulAuthentication 로직을 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authResult) throws IOException, ServletException {

        String userIdentifier = ((AuthUser) authResult.getPrincipal()).getUsername();
        UserDto userDetails = userService.getUserDetailsByEmail(userIdentifier);

        // Access Token 및 Refresh Token 생성
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        // Refresh Token을 Redis에 저장
        refreshTokenService.saveRefreshToken(userDetails.getId(), refreshToken);
        log.info("Local Login Success. Access & Refresh Token generated for user: {}", userDetails.getId());

        // 1. Refresh Token을 HttpOnly 쿠키에 담아 전송
        addRefreshTokenToCookie(response, refreshToken);

        // 2. Access Token을 JSON Body로 응답
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(new TokenResponse(accessToken)));
    }

    private void addRefreshTokenToCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken); // 쿠키 이름 지정

        refreshTokenCookie.setHttpOnly(true);
        // refreshTokenCookie.setSecure(true); // TODO: HTTPS 환경에서는 true로 설정 필요
        refreshTokenCookie.setPath("/"); // 쿠키가 전송될 경로 (전체 경로로 설정)

        // 쿠키 만료 시간 설정 (초 단위)
        int maxAgeInSeconds = (int) (jwtTokenProvider.getRefreshExpirationTimeMillis() / 1000);
        refreshTokenCookie.setMaxAge(maxAgeInSeconds);

        response.addCookie(refreshTokenCookie);
        log.debug("Refresh Token 쿠키 설정 완료.");
    }

}
