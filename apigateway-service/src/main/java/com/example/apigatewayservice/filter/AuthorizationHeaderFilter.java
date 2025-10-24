package com.example.apigatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class AuthorizationHeaderFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_USER_ID_HEADER = "X-User-Id";
    private static final String INTERNAL_USER_EMAIL_HEADER = "X-User-Email";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String secretKey;
    private SecretKey key;
    private JwtParser jwtParser;

    @Value("${app.gateway.excluded-urls}")
    private List<String> excludedUrls;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 생성자 대신 Key, jwtParser 미리 생성해서 성능 향상
    @PostConstruct
    public void init() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser().verifyWith(this.key).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.info("🔍 API Gateway Request Path: {}", path);
        log.info("🔍 Headers: {}", request.getHeaders());

        // 1. 화이트리스트(인증 예외) 경로 검사
        if (isPatchExcluded(path)) {
            log.info("Permitting request to excluded path: {}", path);
            return chain.filter(exchange);
        }

        // 2. Authorization Header 존재 여부 검사
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
        }

        try {
            // 3. 헤더에서 JWT 추출
            String jwt = getJwtFromHeader(request);
            Claims claims = getClaims(jwt);

            // 4. JWT 파싱 및 Claims 추출
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            // 5. Claims에서 사용자 정보 추출 및 검증
            if (isInvalidPayload(userId, email)) {
                log.warn("Invalid JWT payload: userId or email is missing.");
                return onError(exchange, "Invalid JWT payload", HttpStatus.UNAUTHORIZED);
            }

            // 6. [보안] 헤더를 변조하여 내부 서비스로 전달
            ServerHttpRequest newRequest = buildInternalRequest(request, userId, email);

            // 7. 다음 필터 체인 실행
            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (Exception e){
            // getJwtFromHeader 또는 getClaims에서 발생한 모든 예외 (JWT 파싱/검증 실패)
            log.warn("Invalid JWT token processing for path {}: {}", path, e.getMessage());
            return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        log.warn("Gateway Error: {} (Status: {})", message, status);
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    // 필터의 실행 순서 지정 (가장 먼저 실행)
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // -2147483648
    }

    //== 내부 헬퍼 메서드 ==//
    private Boolean isPatchExcluded(String path) {
        return excludedUrls.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String getJwtFromHeader(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Invalid Authorization header format.");
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    private Claims getClaims(String token) {
        return this.jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isInvalidPayload(String userId, String email) {
        return userId == null || userId.isEmpty() || email == null || email.isEmpty();
    }

    private ServerHttpRequest buildInternalRequest(ServerHttpRequest request, String userId, String email) {
        return request.mutate()
                .headers(httpHeaders -> {
                    // 외부에서 유입될 수 있는 내부용 헤더를 먼저 제거 (Header Spoofing 방지)
                    httpHeaders.remove(INTERNAL_USER_ID_HEADER);
                    httpHeaders.remove(INTERNAL_USER_EMAIL_HEADER);

                    // 외부 인증 토큰(JWT) 헤더 제거
                    httpHeaders.remove(HttpHeaders.AUTHORIZATION);

                    // 검증된 정보로 내부용 헤더 추가
                    httpHeaders.add(INTERNAL_USER_ID_HEADER, userId);
                    httpHeaders.add(INTERNAL_USER_EMAIL_HEADER, email);
                })
                .build();
    }

}