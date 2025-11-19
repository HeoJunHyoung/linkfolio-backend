package com.example.chatservice.handler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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

            // Gateway가 넣어준 헤더 추출
            String userId = httpRequest.getHeader("X-User-Id");

            if (userId != null) {
                // STOMP 세션 속성에 저장 (나중에 StompHandler에서 꺼내서 사용함)
                // ㄴ 해당 부분을 구현할 때, "HTTP Handshake랑 StompHandler 호출되는 Pod가 달라지면, 세션 유지를 못 시켜서 userId가 유실되지 않을까?" 라는
                //    생각을 했지만, WebSocket은 Connection-Oriented 방식이라서 해당 문제가 발생하지 않는다는 점 확인했음.
                //    (물리적인 연결이 끊어지지 않는 한, 로드밸런서가 중간에 개입해서 이미 연결된 파이프의 끝단을 Pod 1에서 Pod 2로 옮길 수는 없음)
                attributes.put("X-User-Id", userId);
                log.debug("WebSocket Handshake: UserId {} stored in session attributes", userId);
            } else {
                log.warn("WebSocket Handshake: X-User-Id header missing (Gateway bypass suspected)");
                // 필요 시 여기서 return false; 하여 연결 거부 가능
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Nothing
    }
}
