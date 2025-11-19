package com.example.chatservice.service.redis;

import com.example.chatservice.dto.ChatMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber {

    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Redis에서 메시지가 발행(publish)되면 대기하고 있던 onMessage가 해당 메시지를 받아 처리한다.
     */
    public void sendMessage(String publishMessage) {
        try {
            // Redis에서 받은 JSON 문자열을 객체로 매핑
            ChatMessageResponse chatMessage = objectMapper.readValue(publishMessage, ChatMessageResponse.class);

            // WebSocket 구독자에게 채팅 메시지 Send
            // destination: /topic/chat/{roomId}
            messagingTemplate.convertAndSend("/topic/chat/" + chatMessage.getRoomId(), chatMessage);
            log.info("Redis Subscriber -> WebSocket: room={}, content={}", chatMessage.getRoomId(), chatMessage.getContent());

        } catch (Exception e) {
            log.error("Exception in RedisSubscriber", e);
        }
    }
}