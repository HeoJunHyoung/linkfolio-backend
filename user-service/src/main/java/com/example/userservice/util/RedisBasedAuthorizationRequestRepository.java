package com.example.userservice.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 인증 요청(state 포함)을 세션 대신 Redis에 저장하는 리포지토리.
 * ㄴ STATELESS 환경 및 다중 레플리카(Pod) 환경에서 OAuth2를 일관성 있게 처리하기 위해 사용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBasedAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String OAUTH2_AUTHORIZATION_REQUEST_KEY_PREFIX = "OAUTH2_REQ:";
    public static final String OAUTH2_STATE_COOKIE_NAME = "oauth2_state";

    private static final long STATE_EXPIRATION_MINUTES = 3;

    /**
     * [로드]
     * 1. 쿠키에서 'state' 값을 찾는다.
     * 2. 'state' 값을 키로 사용하여 Redis에서 OAuth2AuthorizationRequest 객체를 조회한다.
     */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        // 1. 쿠키에서 state 값 조회
        String state = getCookie(request, OAUTH2_STATE_COOKIE_NAME)
                .map(Cookie::getValue)
                .orElse(null);

        if (state == null) {
            log.debug("OAuth2 state 쿠키를 찾을 수 없습니다.");
            return null;
        }

        // 2. Redis에서 state 키로 인증 요청 객체 조회
        String redisKey = OAUTH2_AUTHORIZATION_REQUEST_KEY_PREFIX + state;
        OAuth2AuthorizationRequest authorizationRequest = (OAuth2AuthorizationRequest) redisTemplate.opsForValue().get(redisKey);

        if (authorizationRequest == null) {
            log.warn("Redis에 해당 state 키({})가 존재하지 않거나 만료되었습니다.", redisKey);
            return null;
        }

        log.debug("Redis에서 OAuth2 인증 요청 로드 성공. State: {}", state);
        return authorizationRequest;
    }

    /**
     * [저장]
     * 1. authorizationRequest에서 'state' 값을 추출한다.
     * 2. 'state' 값을 키로, authorizationRequest 객체 전체를 값으로 Redis에 저장 (TTL 3분).
     * 3. 'state' 값을 HttpOnly 쿠키로 클라이언트에 저장한다.
     */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            // 인증 요청이 null이면 쿠키와 Redis에서 모두 삭제 (아래 remove 로직 호출)
            removeAuthorizationRequest(request, response);
            return;
        }

        String state = authorizationRequest.getState();
        String redisKey = OAUTH2_AUTHORIZATION_REQUEST_KEY_PREFIX + state;

        // 1. Redis에 인증 요청 객체(state, redirect_uri, scopes 등) 저장
        redisTemplate.opsForValue().set(
                redisKey,
                authorizationRequest, // 객체 자체가 직렬화되어 저장됨 (RedisConfig 덕분)
                STATE_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );

        // 2. 클라이언트에는 'state' 값만 쿠키로 저장
        addCookie(response, OAUTH2_STATE_COOKIE_NAME, state, (int) (STATE_EXPIRATION_MINUTES * 60));
        log.debug("Redis와 쿠키에 OAuth2 state 저장 성공. State: {}", state);
    }

    /**
     * [삭제]
     * 인증 성공/실패 시, Redis와 쿠키에 저장된 state를 모두 삭제한다.
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        // 1. 쿠키를 기반으로 Redis에 저장된 객체를 먼저 로드
        OAuth2AuthorizationRequest authorizationRequest = this.loadAuthorizationRequest(request);

        if (authorizationRequest != null) {
            // 2. Redis에서 삭제
            String state = authorizationRequest.getState();
            String redisKey = OAUTH2_AUTHORIZATION_REQUEST_KEY_PREFIX + state;
            redisTemplate.delete(redisKey);
            log.debug("Redis에서 OAuth2 인증 요청 삭제. State: {}", state);
        }

        // 3. 쿠키 만료 (값을 ""로, MaxAge를 0으로 설정)
        addCookie(response, OAUTH2_STATE_COOKIE_NAME, "", 0);
        log.debug("OAuth2 state 쿠키 만료 처리.");

        return authorizationRequest; // 인터페이스 규약상 삭제된 객체를 반환
    }

    // --- 쿠키 헬퍼 메서드 ---

    private Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return Optional.of(cookie);
                }
            }
        }
        return Optional.empty();
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        // cookie.setSecure(true); // TODO: HTTPS 환경에서는 true로 설정 필요
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}