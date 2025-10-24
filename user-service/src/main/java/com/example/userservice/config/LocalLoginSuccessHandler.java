package com.example.userservice.config;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.UserDto;
import com.example.userservice.service.UserService;
import com.example.userservice.util.JwtTokenProvider;
import jakarta.servlet.ServletException;
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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authResult) throws IOException, ServletException {

        // 1. 인증 객체(AuthUser)에서 이메일(username) 추출
        String userIdentifier = ((AuthUser) authResult.getPrincipal()).getUsername();

        // 2. 이메일로 UserDto 조회 (토큰 생성에 필요한 userId 포함)
        UserDto userDetails = userService.getUserDetailsByEmail(userIdentifier);

        // 3. JWT 토큰 생성
        String token = jwtTokenProvider.generateToken(userDetails);
        log.info("Local Login Success. JWT Token generated for user: {}", userDetails.getId());

        // 4. 응답 헤더에 "Authorization" 헤더 추가 (API 응답 방식)
        response.addHeader("Authorization", "Bearer " + token);
    }
}
