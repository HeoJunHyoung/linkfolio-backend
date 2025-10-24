package com.example.userservice.util;

import com.example.userservice.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser; // ✅ [추가]
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationTimeMillis;
    private final long refreshExpirationTimeMillis;
    private final JwtParser jwtParser; // 재사용을 위한 JwtParser 인스턴스

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access_expiration_time}") String accessExpirationTime,   // [수정] 주입 프로퍼티 변경
                            @Value("${jwt.refresh_expiration_time}") String refreshExpirationTime) { // [추가] Refresh 만료 시간 주입
        byte[] secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(secretKeyBytes);
        this.accessExpirationTimeMillis = Long.parseLong(accessExpirationTime); // [수정] 필드명 변경
        this.refreshExpirationTimeMillis = Long.parseLong(refreshExpirationTime); // [추가] 필드 초기화

        this.jwtParser = Jwts.parser()
                .verifyWith(this.secretKey)
                .build();
    }

    // Access Token 생성
    public String generateAccessToken(UserDto userDetails) {
        Date expirationDate = new Date(System.currentTimeMillis() + accessExpirationTimeMillis);

        return Jwts.builder()
                .subject(userDetails.getId().toString())
                .claim("email", userDetails.getEmail()) // Access Token에는 이메일 포함
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 생성
    public String generateRefreshToken(UserDto userDetails) {
        Date expirationDate = new Date(System.currentTimeMillis() + refreshExpirationTimeMillis);

        return Jwts.builder()
                .subject(userDetails.getId().toString()) // Refresh Token에는 사용자 ID만 포함 (Payload 최소화)
                .expiration(expirationDate)
                .signWith(secretKey)
                .compact();
    }

    private Claims getClaimsFromTokenEvenIfExpired(String token) {
        try {
            return this.jwtParser
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 만료되었더라도 payload는 반환
            return e.getClaims();
        }
        // 그 외 예외 (서명 오류 등)는 그대로 던져짐
    }

    /**
     * 토큰 유효성 검사 메서드
     */
    public boolean validateToken(String token) {
        try {
            this.jwtParser.parseSignedClaims(token); // 'parseClaimsJws' -> 'parseSignedClaims'
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("잘못된 JWT 서명입니다. Token: {}", token, e);
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다. Token: {}", token, e);
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다. Token: {}", token, e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 잘못되었습니다. Token: {}", token, e);
        }
        return false;
    }

    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromTokenEvenIfExpired(token); // 내부 호출 변경
        return claims.getSubject();
    }

    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromTokenEvenIfExpired(token); // 내부 호출 변경
        return claims.get("email", String.class);
    }

    public long getRefreshExpirationTimeMillis() {
        return this.refreshExpirationTimeMillis;
    }

}