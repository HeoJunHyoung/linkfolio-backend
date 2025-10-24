package com.example.userservice.controller;

import com.example.userservice.dto.*;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.service.RefreshTokenService;
import com.example.userservice.service.UserService;
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
    private final JwtTokenProvider jwtTokenProvider;

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

    @PostMapping("/users/refresh")
    public ResponseEntity<TokenResponse> refreshAccessToken(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null) {
            log.warn("Refresh Token 쿠키가 존재하지 않습니다.");
            // ErrorCode에 쿠키 누락 관련 코드 추가 고려
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 1. 서비스 호출 (Map<String, String> 반환)
        Map<String, String> newTokens = refreshTokenService.reissueTokens(refreshToken);
        String newAccessToken = newTokens.get("accessToken");
        String newRefreshToken = newTokens.get("refreshToken"); // Map에서 새 Refresh Token 꺼내기

        // 2. 응답: 새 Refresh Token은 HttpOnly 쿠키로 설정
        addRefreshTokenToCookie(response, newRefreshToken); // 꺼낸 Refresh Token 사용

        // 3. 응답: 새 Access Token은 JSON Body로 반환 (TokenResponse DTO 사용)
        log.info("Access Token 재발급 및 Refresh Token 쿠키 갱신 완료.");
        return ResponseEntity.ok(new TokenResponse(newAccessToken)); // 새 Access Token만 담아서 응답
    }

    @PostMapping("/users/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser authUser, HttpServletResponse response) {

        if (authUser == null) { // 인증되지 않은 사용자의 로그아웃 요청 (예: 토큰 만료 후)
            log.warn("인증 정보 없이 로그아웃 시도됨.");
            expireRefreshTokenCookie(response); // 클라이언트 측 쿠키만 만료시킴
            return ResponseEntity.ok().build();
        }

        Long userId = authUser.getUserId();
        log.info("로그아웃 요청. UserId: {}", userId);

        // 1. Redis에서 해당 유저의 Refresh Token 삭제
        refreshTokenService.deleteRefreshToken(userId);

        // 2. 클라이언트의 Refresh Token 쿠키 만료 시키기
        expireRefreshTokenCookie(response);

        log.info("로그아웃 처리 완료. UserId: {}", userId);
        return ResponseEntity.ok().build();
    }


    // === 컨트롤러 내 쿠키 처리 헬퍼 메서드 ===
    private void addRefreshTokenToCookie(HttpServletResponse response, String refreshToken) {
        Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        // refreshTokenCookie.setSecure(true); // TODO: HTTPS 환경에서는 true 설정
        refreshTokenCookie.setPath("/");
        int maxAgeInSeconds = (int) (jwtTokenProvider.getRefreshExpirationTimeMillis() / 1000);
        refreshTokenCookie.setMaxAge(maxAgeInSeconds);
        response.addCookie(refreshTokenCookie);
        log.debug("새 Refresh Token 쿠키 설정 완료.");
    }

    private void expireRefreshTokenCookie(HttpServletResponse response) {
        Cookie refreshTokenCookie = new Cookie("refresh_token", null); // value를 null로 설정
        refreshTokenCookie.setHttpOnly(true);
        // refreshTokenCookie.setSecure(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0); // 만료 시간을 0으로 설정하여 즉시 삭제
        response.addCookie(refreshTokenCookie);
        log.debug("Refresh Token 쿠키 만료 처리 완료.");
    }

}
