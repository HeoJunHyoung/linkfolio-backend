package com.example.authservice.handler;

import com.example.authservice.dto.AuthUser;
import com.example.authservice.dto.UserDto;
import com.example.authservice.dto.response.TokenResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.RefreshTokenService;
import com.example.authservice.util.CookieUtil;
import com.example.authservice.util.JwtTokenProvider;
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

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final ObjectMapper objectMapper;
    private final CookieUtil cookieUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authResult) throws IOException, ServletException {

        String userIdentifier = ((AuthUser) authResult.getPrincipal()).getUsername();
        UserDto userDetails = authService.getUserDetailsByEmail(userIdentifier);

        // Access Token 및 Refresh Token 생성
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        // Refresh Token을 Redis에 저장
        refreshTokenService.saveRefreshToken(userDetails.getId(), refreshToken);
        log.info("Local Login Success. Access & Refresh Token generated for user: {}", userDetails.getId());

        // 1. Refresh Token을 HttpOnly 쿠키에 담아 전송
        cookieUtil.addRefreshTokenCookie(response, refreshToken);

        // 2. Access Token을 JSON Body로 응답
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(new TokenResponse(accessToken)));
    }

}
