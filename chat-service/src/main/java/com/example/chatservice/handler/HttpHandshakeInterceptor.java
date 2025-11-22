package com.example.chatservice.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class HttpHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();

            // HTTP 요청 헤더에 있는 X-User-Id (Gateway가 검증 후 넣어준 값)를 추출
            String userId = httpRequest.getHeader("X-User-Id");

            if (userId != null) {
                // 이 ID를 WebSocket 세션의 속성(attributes)에 저장
                attributes.put("X-User-Id", userId);
                log.debug("WebSocket Handshake: UserId {} stored in session attributes", userId);
            } else {
                log.warn("WebSocket Handshake: X-User-Id header missing (Gateway bypass suspected)");
                response.setStatusCode(HttpStatus.UNAUTHORIZED); // 401 응답 설정
                return false; // 연결 거부
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Nothing
    }
}
