package com.example.apigatewayservice.filter;

import io.jsonwebtoken.Claims;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class AuthorizationHeaderFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secretKey;
    private SecretKey key;

    @Value("${app.gateway.excluded-urls}")
    private List<String> excludedUrls;

    // 생성자 대신 Key 미리 생성해서 성능 향상
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        log.info("🔍 API Gateway Request Path: {}", path);
        log.info("🔍 Headers: {}", request.getHeaders());

        // 화이트리스트 검사 로직 (YML 기반)
        boolean isExcluded = excludedUrls.stream().anyMatch(path::startsWith);
        if (isExcluded) {
            log.info("Permitting request to {}", path);
            return chain.filter(exchange);
        }

        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String jwt = authHeader.replace("Bearer ", "");

        Claims claims;
        try {
            claims = getClaims(jwt);
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
        }

        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        if (userId == null || userId.isEmpty() || email == null || email.isEmpty()) {
            return onError(exchange, "Invalid JWT payload", HttpStatus.UNAUTHORIZED);
        }

        ServerHttpRequest newRequest = request.mutate()
                .header("X-User-Id", userId)     // 내부용 헤더 추가
                .header("X-User-Email", email)   // 내부용 헤더 추가
                .headers(httpHeaders -> httpHeaders.remove(HttpHeaders.AUTHORIZATION)) // [보안] 외부 토큰 제거
                .build();

        return chain.filter(exchange.mutate().request(newRequest).build());
    }


    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }


    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(this.key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    // 필터의 실행 순서 지정 (가장 먼저 실행)
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // -2147483648
    }

}