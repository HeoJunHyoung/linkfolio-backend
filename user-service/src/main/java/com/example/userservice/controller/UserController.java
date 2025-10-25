package com.example.userservice.controller;

import com.example.userservice.dto.*;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.service.RefreshTokenService;
import com.example.userservice.service.UserService;
import com.example.userservice.util.CookieUtil;
import com.example.userservice.util.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
@Slf4j
public class UserController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to LinkFolio Project";
    }

    // 회원가입
    @PostMapping("/users/signup")
    public ResponseEntity<Void> signUpApi(@RequestBody UserSignUpRequest request) {
        userService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 로그인
    @PostMapping("/users/login")
    public void login(@RequestBody UserLoginRequest request) {
        // 로그인 처리는 AuthenticationFilter 위임
    }

    // 내 정보 조회
    @GetMapping("/users/me")
    public ResponseEntity<UserResponse> getMyInfoApi(@AuthenticationPrincipal AuthUser authUser) {
        UserResponse userResponse = userService.getUser(authUser.getUserId());
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

    // 특정 회원 단일 조회
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> getUserApi(@PathVariable("userId") Long userId) {
        UserResponse userResponse = userService.getUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

    // token 재발급
    @PostMapping("/users/refresh")
    public ResponseEntity<TokenResponse> refreshAccessToken(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                                            HttpServletResponse response) {

        if (refreshToken == null) {
            log.warn("Refresh Token 쿠키가 존재하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, String> newTokens = refreshTokenService.reissueTokens(refreshToken);
        String newAccessToken = newTokens.get("accessToken");
        String newRefreshToken = newTokens.get("refreshToken");

        cookieUtil.addRefreshTokenCookie(response, newRefreshToken);

        log.info("Access Token 재발급 및 Refresh Token 쿠키 갱신 완료.");
        return ResponseEntity.ok(new TokenResponse(newAccessToken));
    }

    // 로그아웃
    @PostMapping("/users/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser authUser, HttpServletResponse response) {

        if (authUser == null) {
            log.warn("인증 정보 없이 로그아웃 시도됨.");
            cookieUtil.expireRefreshTokenCookie(response);
            return ResponseEntity.ok().build();
        }

        Long userId = authUser.getUserId();
        log.info("로그아웃 요청. UserId: {}", userId);

        refreshTokenService.deleteRefreshToken(userId);
        cookieUtil.expireRefreshTokenCookie(response);

        log.info("로그아웃 처리 완료. UserId: {}", userId);
        return ResponseEntity.ok().build();
    }

}
