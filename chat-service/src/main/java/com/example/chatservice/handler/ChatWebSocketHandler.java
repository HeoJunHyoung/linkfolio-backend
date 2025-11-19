package com.example.chatservice.handler;

import com.example.chatservice.dto.ChatMessageDto;
import com.example.chatservice.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1. WebSocket 연결/해제/메시지 수신 처리
 * 2. Redis Pub/Sub 메시지를 받아 WebSocket으로 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    // {roomId: {userId: WebSocketSession}}
    private final Map<String, Map<Long, WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    private final UriTemplate uriTemplate = new UriTemplate("/ws-chat/{roomId}");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().getPath();
        Map<String, String> pathVariables = uriTemplate.match(uri);
        String roomId = pathVariables.get("roomId");

        // 인증된 사용자 ID 추출 (InternalHeaderAuthenticationFilter에서 등록된 AuthUser 사용)
        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            session.close(CloseStatus.BAD_DATA);
            log.warn("WebSocket 연결 실패: 인증 정보(X-User-Id) 누락.");
            return;
        }

        // 세션 등록
        sessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, session);
        log.info("WebSocket 연결 성공. RoomId: {}, UserId: {}", roomId, userId);

        // 1. Redis에 접속 상태 PUBLISH
        chatService.publishUserOnlineStatus(roomId, userId, true);

        // 2. 메시지 읽음 처리 (READ 이벤트 발생)
        chatService.handleReadMessage(roomId, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            ChatMessageDto chatMessage = objectMapper.readValue(payload, ChatMessageDto.class);
            Long senderId = getUserIdFromSession(session);

            if (senderId == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            chatMessage.setSenderId(senderId);

            switch (chatMessage.getType()) {
                case TALK:
                    chatService.handleSendMessage(chatMessage).subscribe(); // 메시지 전송 및 저장
                    break;
                case READ:
                    chatService.handleReadMessage(chatMessage.getRoomId(), chatMessage.getSenderId()).subscribe(); // 메시지 읽음
                    break;
                case TYPING:
                    chatService.publishTypingStatus(chatMessage.getRoomId(), chatMessage.getSenderId(), chatMessage.getContent());
                    break;
                default:
                    log.warn("Unsupported message type: {}", chatMessage.getType());
            }

        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String uri = session.getUri().getPath();
        Map<String, String> pathVariables = uriTemplate.match(uri);
        String roomId = pathVariables.get("roomId");
        Long userId = getUserIdFromSession(session);

        if (userId != null && sessions.containsKey(roomId)) {
            sessions.get(roomId).remove(userId);
            if (sessions.get(roomId).isEmpty()) {
                sessions.remove(roomId);
            }
            log.info("WebSocket 연결 종료. RoomId: {}, UserId: {}", roomId, userId);

            // Redis에 접속 상태 PUBLISH
            chatService.publishUserOnlineStatus(roomId, userId, false);
        }
    }

    // 특정 방에 메시지 전송
    public void sendMessage(String roomId, ChatMessageDto message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("JSON 직렬화 오류", e);
            return;
        }

        Map<Long, WebSocketSession> roomSessions = sessions.get(roomId);
        if (roomSessions == null) return;

        roomSessions.values().parallelStream().forEach(session -> {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.error("WebSocket 메시지 전송 오류", e);
            }
        });
    }

    // WebSocketSession에서 인증된 사용자 ID 추출
    private Long getUserIdFromSession(WebSocketSession session) {
        Authentication auth = (Authentication) session.getPrincipal();
        // AuthUser는 common-module에 정의되어 있음
        if (auth != null && auth.getPrincipal() instanceof com.example.commonmodule.dto.security.AuthUser authUser) {
            return authUser.getUserId(); // AuthUser.userId 반환
        }
        return null;
    }
}