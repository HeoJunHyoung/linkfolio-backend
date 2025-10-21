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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private final SecretKey secretKey;
    private final long expirationTimeMillis;
    private final JwtParser jwtParser; // ✅ [추가] 재사용을 위한 JwtParser 인스턴스

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration_time}") String expirationTime) {
        byte[] secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(secretKeyBytes);
        this.expirationTimeMillis = Long.parseLong(expirationTime);

        // ✅ [수정] JwtParser를 생성 시점에 미리 빌드
        this.jwtParser = Jwts.parser()
                .verifyWith(this.secretKey) // ✅ 'setSigningKey' 대신 'verifyWith' 사용
                .build();
    }

    /**
     * 로그인 성공 시 토큰 생성
     */
    public String generateToken(UserDto userDetails) {
        Date expirationDate = new Date(System.currentTimeMillis() + expirationTimeMillis);

        // JWT Subject : userId
        // JWT Claims  : email
        return Jwts.builder()
                .subject(userDetails.getId().toString()) // ✅ [수정] 'setSubject' -> 'subject'
                .claim("email", userDetails.getEmail())
                .expiration(expirationDate) // ✅ [수정] 'setExpiration' -> 'expiration'
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰 유효성 검사 메서드
     */
    public boolean validateToken(String token) {
        try {
            // ✅ [수정] 미리 빌드해둔 jwtParser로 파싱
            this.jwtParser.parseSignedClaims(token); // ✅ 'parseClaimsJws' -> 'parseSignedClaims'
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

    /**
     * 토큰에서 사용자 ID(Subject) 추출 메서드
     */
    public String getUserIdFromToken(String token) {
        // ✅ [수정] 미리 빌드해둔 jwtParser로 파싱
        Claims claims = this.jwtParser
                .parseSignedClaims(token) // ✅ 'parseClaimsJws' -> 'parseSignedClaims'
                .getPayload(); // ✅ [수정] 'getBody' -> 'getPayload'

        // Claims에서 Subject(사용자 ID)를 반환
        return claims.getSubject();
    }

    /**
     * 토큰에서 이메일(Custom Claim) 추출 메서드
     */
    public String getEmailFromToken(String token) {
        // ✅ [수정] 미리 빌드해둔 jwtParser로 파싱
        Claims claims = this.jwtParser
                .parseSignedClaims(token) // ✅ 'parseClaimsJws' -> 'parseSignedClaims'
                .getPayload(); // ✅ [수정] 'getBody' -> 'getPayload'

        // Claims에서 "email" 필드를 String으로 반환
        return claims.get("email", String.class);
    }
}