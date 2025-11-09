package com.example.authservice.service;

import com.example.authservice.dto.UserDto;
import com.example.authservice.exception.ErrorCode;
import com.example.authservice.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REDIS_KEY_PREFIX = "RT:"; // Refresh Token 저장 키 접두사

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthService authService;

    /**
     * Refresh Token을 Redis에 저장 (기존 토큰 덮어쓰기)
     */
    public void saveRefreshToken(Long userId, String refreshToken) {
        String key = REDIS_KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(
                key,
                refreshToken, // 값은 JSON 문자열 형태("")로 저장됨
                jwtTokenProvider.getRefreshExpirationTimeMillis(),
                TimeUnit.MILLISECONDS
        );
        log.info("Redis에 Refresh Token 저장 완료 (UserId: {})", userId);
    }

    /**
     * [RTR] 토큰 재발급
     * @param refreshToken 쿠키에서 추출한 Refresh Token
     * @return 새로운 Access Token과 Refresh Token이 담긴 응답 객체
     */
    public Map<String, String> reissueTokens(String refreshToken) { // 반환 타입 Map으로 변경
        Long userId;
        try {
            userId = Long.parseLong(jwtTokenProvider.getUserIdFromToken(refreshToken));
        } catch (Exception e) {
            log.warn("유효하지 않은 Refresh Token 형식 또는 서명 오류. Token: {}", refreshToken, e);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String key = REDIS_KEY_PREFIX + userId;
        Object storedTokenObj = redisTemplate.opsForValue().get(key);

        if (storedTokenObj == null) {
            log.warn("Redis에 Refresh Token 없음 (만료 또는 삭제됨). UserId: {}", userId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        String storedRefreshToken = (String) storedTokenObj;

        if (!storedRefreshToken.equals(refreshToken)) {
            log.warn("Refresh Token 불일치 (탈취 의심). UserId: {}", userId);
            redisTemplate.delete(key);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        // --- 토큰 검증 완료 ---

        UserDto userDetails = authService.getUserDetailsById(userId);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        // Redis에 새로운 Refresh Token 갱신
        saveRefreshToken(userId, newRefreshToken);

        log.info("Access/Refresh Token 재발급 성공. UserId: {}", userId);

        // Map에 두 토큰을 담아 반환
        return Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        );
    }

    /**
     * 로그아웃 시 Redis에서 Refresh Token 삭제
     */
    public void deleteRefreshToken(Long userId) {
        String key = REDIS_KEY_PREFIX + userId;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Redis에서 Refresh Token 삭제 완료. UserId: {}", userId);
        } else {
            log.warn("삭제할 Refresh Token이 Redis에 존재하지 않음. UserId: {}", userId);
        }
    }
}