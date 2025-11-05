package com.example.authservice.util;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final JwtTokenProvider jwtTokenProvider; // Refresh Token 만료 시간 가져오기 위해 주입

    /**
     * Refresh Token을 HttpOnly 쿠키로 설정하는 메서드
     * @param response HttpServletResponse 객체
     * @param refreshToken 쿠키에 담을 Refresh Token 값
     */
//    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
//        Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken); // 쿠키 이름 지정
//        refreshTokenCookie.setHttpOnly(true); // JavaScript 접근 방지
//        // refreshTokenCookie.setSecure(true); // TODO: HTTPS 환경에서는 이 옵션을 true로 설정해야 합니다.
//        refreshTokenCookie.setPath("/"); // 쿠키가 전송될 경로 (애플리케이션 전역)
//
//        // 쿠키 만료 시간 설정 (초 단위, Refresh Token 만료 시간과 동일하게 설정)
//        int maxAgeInSeconds = (int) (jwtTokenProvider.getRefreshExpirationTimeMillis() / 1000);
//        refreshTokenCookie.setMaxAge(maxAgeInSeconds);
//
//        response.addCookie(refreshTokenCookie); // 응답에 쿠키 추가
//        log.debug("새 Refresh Token 쿠키 설정 완료.");
//    }

    /**
     * Refresh Token 쿠키를 만료시키는 메서드 (로그아웃 시 사용)
     * @param response HttpServletResponse 객체
     */
//    public void expireRefreshTokenCookie(HttpServletResponse response) {
//        Cookie refreshTokenCookie = new Cookie("refresh_token", null); // 쿠키 값을 null로 설정
//        refreshTokenCookie.setHttpOnly(true);
//        // refreshTokenCookie.setSecure(true);
//        refreshTokenCookie.setPath("/");
//        refreshTokenCookie.setMaxAge(0); // 만료 시간을 0으로 설정하여 즉시 삭제
//        response.addCookie(refreshTokenCookie); // 응답에 만료된 쿠키 추가 (덮어쓰기)
//        log.debug("Refresh Token 쿠키 만료 처리 완료.");
//    }

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        int maxAgeInSeconds = (int) (jwtTokenProvider.getRefreshExpirationTimeMillis() / 1000);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeInSeconds)
                .sameSite("None")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("새 Refresh Token 쿠키 설정 완료 (Secure, SameSite=None 적용).");
    }

    public void expireRefreshTokenCookie(HttpServletResponse response) {
        // ResponseCookie 빌더를 사용하여 만료 쿠키 생성
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();

        // response.addHeader()로 Set-Cookie 헤더 직접 추가
        response.addHeader("Set-Cookie", cookie.toString());
        log.debug("Refresh Token 쿠키 만료 처리 완료 (Secure, SameSite=None 적용).");
    }
}