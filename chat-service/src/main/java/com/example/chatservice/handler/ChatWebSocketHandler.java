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
    private final Map<String, Map<Long, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    private final UriTemplate uriTemplate = new UriTemplate("/ws-chat/{roomId}");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String uri = session.getUri().getPath();
        Map<String, String> pathVariables = uriTemplate.match(uri);
        String roomId = pathVariables.get("roomId");

        Long userId = getUserIdFromSession(session);
        if (userId == null) {
            session.close(CloseStatus.BAD_DATA);
            log.warn("WebSocket 연결 실패: 인증 정보(X-User-Id) 누락.");
            return;
        }

        // Room Session 등록
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, session);

        // Redis에 현재 접속 중인 방 ID 저장
        chatService.saveUserCurrentRoom(userId, roomId).subscribe();

        log.info("WebSocket 연결 성공. RoomId: {}, UserId: {}", roomId, userId);

        // Redis에 접속 상태 PUBLISH
        chatService.publishUserOnlineStatus(roomId, userId, true).subscribe();

        // 채팅방에 들어온 순간 READ 처리
        chatService.handleReadMessage(roomId, userId).subscribe();
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
                    // WebSocket 연결 시점에 이미 처리되므로, 클라이언트 요청으로 처리할 필요는 적지만, 명세에 따라 유지
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

        if (userId != null && roomSessions.containsKey(roomId)) {
            roomSessions.get(roomId).remove(userId);
            if (roomSessions.get(roomId).isEmpty()) {
                roomSessions.remove(roomId);
            }
            log.info("WebSocket 연결 종료. RoomId: {}, UserId: {}", roomId, userId);

            // Redis에 접속 상태 PUBLISH 및 현재 접속 중인 방 ID 제거
            chatService.publishUserOnlineStatus(roomId, userId, false).subscribe();
            chatService.removeUserCurrentRoom(userId).subscribe();
        }
    }

    /**
     * 특정 방에 메시지 전송 (메시지, READ, TYPING, ENTER/EXIT 전파)
     * RedisMessageSubscriber에서 호출됨.
     */
    public void sendMessageToRoom(String roomId, ChatMessageDto message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("JSON 직렬화 오류", e);
            return;
        }

        Map<Long, WebSocketSession> room = roomSessions.get(roomId);
        if (room == null) return;

        room.values().parallelStream().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.error("WebSocket 메시지 전송 오류", e);
            }
        });
    }

    /**
     * 특정 사용자에게 글로벌 알림 메시지 전송 (예: 채팅방 목록 갱신 알림)
     * RedisMessageSubscriber에서 호출됨.
     */
    public void sendGlobalNotificationToUser(Long userId, ChatMessageDto message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("JSON 직렬화 오류", e);
            return;
        }

        // 현재 로직상, 사용자가 어떤 방에 접속 중이라면 그 세션을 사용합니다. (가장 최근 세션으로 간주)
        roomSessions.values().stream()
                .flatMap(room -> room.entrySet().stream())
                .filter(entry -> entry.getKey().equals(userId))
                .map(Map.Entry::getValue)
                .forEach(session -> {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(payload));
                        }
                    } catch (IOException e) {
                        log.error("WebSocket 글로벌 알림 전송 오류", e);
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