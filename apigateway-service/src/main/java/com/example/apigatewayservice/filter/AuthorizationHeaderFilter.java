package com.example.apigatewayservice.filter;

import com.example.apigatewayservice.exception.ErrorCode;
import com.example.apigatewayservice.exception.GatewayAuthenticationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
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
    private final ObjectMapper objectMapper;

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
            return onError(exchange, ErrorCode.MISSING_AUTH_HEADER);
        }

        try {
            // 3. 헤더에서 JWT 추출 (GatewayAuthenticationException 발생 가능)
            String jwt = getJwtFromHeader(request);
            // 4. JWT 파싱 및 Claims 추출 (JwtException 발생 가능)
            Claims claims = getClaims(jwt);

            // 5. Claims에서 사용자 정보 추출
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            // 6. Claims 검증
            if (isInvalidPayload(userId, email)) {
                log.warn("Invalid JWT payload: userId or email is missing.");
                return onError(exchange, ErrorCode.INVALID_JWT_PAYLOAD);
            }

            // 7. [보안] 스푸핑 공격을 방지하도록 헤더 수정 후, 내부 서비스로 전달
            ServerHttpRequest newRequest = buildInternalRequest(request, userId, email);

            // 8. 다음 필터 체인 실행
            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (GatewayAuthenticationException e) {
            log.warn("Gateway Authentication Error for path {}: {}", path, e.getMessage());
            return onError(exchange, e.getErrorCode());
        } catch (JwtException e) {
            // JWT 파싱/검증 실패 (서명, 만료, 형식 오류 등)
            log.warn("Invalid JWT token processing for path {}: {}", path, e.getMessage());
            return onError(exchange, ErrorCode.INVALID_JWT_TOKEN);
        } catch (Exception e) {
            // 그 외 예기치 못한 오류
            log.error("Unexpected error in AuthorizationHeaderFilter: {}", e.getMessage(), e);
            return onError(exchange, ErrorCode.INTERNAL_FILTER_ERROR);
        }
    }

    /**
     * 에러 응답을 JSON 형식으로 반환하도록 수정
     */
    private Mono<Void> onError(ServerWebExchange exchange, ErrorCode errorCode) {
        log.warn("Gateway Error: {} (Status: {})", errorCode.getMessage(), errorCode.getStatus());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(errorCode.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 클라이언트에 반환할 표준 Error DTO 생성
        Map<String, Object> errorResponse = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", errorCode.getStatus().value(),
                "code", errorCode.getCode(),
                "message", errorCode.getMessage()
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response to JSON", e);
            // JSON 직렬화 실패 시, 상태 코드만 설정
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Boolean isPatchExcluded(String path) {
        return excludedUrls.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }


    /**
     * 유효성 검사 실패 시, GatewayAuthenticationException을 던지도록 변경
     */
    private String getJwtFromHeader(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new GatewayAuthenticationException(ErrorCode.INVALID_AUTH_FORMAT);
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }

    /**
     * 예외를 잡지 않고, 호출한 곳(filter 메서드)으로 전파 (JwtException)
     */
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
                    httpHeaders.remove(INTERNAL_USER_ID_HEADER);
                    httpHeaders.remove(INTERNAL_USER_EMAIL_HEADER);
                    httpHeaders.remove(HttpHeaders.AUTHORIZATION);

                    httpHeaders.add(INTERNAL_USER_ID_HEADER, userId);
                    httpHeaders.add(INTERNAL_USER_EMAIL_HEADER, email);
                })
                .build();
    }
}