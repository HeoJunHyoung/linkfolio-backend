package com.example.chatservice.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    /**
     * HTTP 연결이 WebSocket(ws://)으로 업그레이드된 상태에서 클라이언트가 STOMP CONNECT 프레임 전송
     * ㄴ [1] StompHandler의 preSend() 메서드가 CONNECT 요청을 감지
     * ㄴ [2] HttpHandshakeInterceptor가 세션에 넣어둔 X-User-Id를 추출
     * ㄴ [3] UsernamePasswordAuthenticationToken(Principal)을 생성하여 해당 세션의 주인(User)으로 등록
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // HandshakeInterceptor에서 넣어둔 세션 속성 가져오기
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

            if (sessionAttributes != null && sessionAttributes.containsKey("X-User-Id")) {
                String userIdStr = (String) sessionAttributes.get("X-User-Id");
                Long userId = Long.parseLong(userIdStr);

                // STOMP Principal 설정 (ChatSocketController에서 Principal로 사용 가능)
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, null);
                accessor.setUser(authentication);

                log.info("STOMP Connection Authenticated: UserId {}", userId);
            }
        }
        return message;
    }
}