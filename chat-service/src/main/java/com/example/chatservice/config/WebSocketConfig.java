package com.example.chatservice.config;

import com.example.chatservice.handler.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // /ws-chat/{roomId} 경로로 WebSocket 연결을 설정
        // ㄴ 주소는 Gateway의 라우팅 설정(Path=/ws-chat/**)과 일치
        registry.addHandler(chatWebSocketHandler, "/ws-chat/{roomId}")
                .setAllowedOriginPatterns("*"); // CORS를 위해 모든 Origin 허용
    }
}