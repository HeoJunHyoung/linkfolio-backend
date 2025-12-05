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
    private static final String INTERNAL_USER_ROLE_HEADER = "X-User-Role";
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
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath(); // 현재 요청 경로 추출

        log.info("🔍 API Gateway Request Path: {}", path);

        // 1. 토큰 추출 시도
        String token = resolveToken(request);

        // 2. 토큰이 없는 경우 처리
        if (token == null) {
            // 화이트리스트(인증 예외) 경로라면 인증 없이 통과 (비로그인 요청 중에서도 인증 없이 가능한 API 호출)
            if (isPatchExcluded(path)) {
                log.info("Permitting anonymous request to excluded path: {}", path);
                return chain.filter(exchange);
            }
            // 화이트리스트가 아니라면 에러 반환
            return onError(exchange, ErrorCode.MISSING_AUTH_HEADER);
        }

        // 3. 토큰이 있는 경우 검증 및 헤더 주입 (화이트리스트 경로라도 토큰이 있으면 수행)
        try {
            // JWT 파싱 및 Claims 추출 (JwtException 발생 가능)
            Claims claims = getClaims(token);

            // Claims에서 사용자 정보 추출 (userId, email, role)
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);

            // Claims 검증 ; 값 존재 여부 확인
            if (isInvalidPayload(userId, email, role)) {
                log.warn("Invalid JWT payload: userId or email is missing.");
                return onError(exchange, ErrorCode.INVALID_JWT_PAYLOAD);
            }

            // buildInternalRequest 메서드를 호출하여 새로운 요청(Request)을 생성 => 스푸핑 공격 방지
            // (X-User-Id 헤더 주입)
            ServerHttpRequest newRequest = buildInternalRequest(request, userId, email, role);

            // 다음 필터 체인 실행 (인증된 정보 포함)
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
     * 토큰 추출 메서드 (Header -> Query Param 순서로 확인)
     */
    private String resolveToken(ServerHttpRequest request) {
        // 1. Authorization 헤더 확인
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        // 2. 쿼리 파라미터 확인 (웹소켓 등 헤더 사용 불가 시나리오)
        String queryToken = request.getQueryParams().getFirst("token");
        if (queryToken != null && !queryToken.isEmpty()) {
            return queryToken;
        }

        return null; // 토큰을 찾을 수 없음
    }

    /**
     * 에러 응답을 JSON 형식으로 반환
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
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }

    private Boolean isPatchExcluded(String path) {
        return excludedUrls.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 예외를 잡지 않고, 호출한 곳(filter 메서드)으로 전파 (JwtException)
     */
    private Claims getClaims(String token) {
        return this.jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isInvalidPayload(String userId, String email, String role) {
        return userId == null || userId.isEmpty() || email == null || email.isEmpty() || role == null || role.isEmpty();
    }

    private ServerHttpRequest buildInternalRequest(ServerHttpRequest request, String userId, String email, String role) {
        return request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove(INTERNAL_USER_ID_HEADER);
                    httpHeaders.remove(INTERNAL_USER_EMAIL_HEADER);
                    httpHeaders.remove(INTERNAL_USER_ROLE_HEADER);
                    httpHeaders.remove(HttpHeaders.AUTHORIZATION); // 내부 통신 시 Authorization 헤더 제거

                    httpHeaders.add(INTERNAL_USER_ID_HEADER, userId);
                    httpHeaders.add(INTERNAL_USER_EMAIL_HEADER, email);
                    httpHeaders.add(INTERNAL_USER_ROLE_HEADER, role);
                })
                .build();
    }
}