package com.example.chatservice.service.redis;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.enumerate.MessageType;
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
            ChatMessageResponse message = objectMapper.readValue(publishMessage, ChatMessageResponse.class);

            if (message.getType() == MessageType.READ) {
                messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId() + "/read", message); // 읽음 표시
            } else if (message.getType() == MessageType.TYPING) {
                messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId() + "/typing", message); // 입력중
            } else {
                messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), message);     // 메시지 전송
                messagingTemplate.convertAndSend("/topic/user/" + message.getReceiverId(), message); // 알림
            }

        } catch (Exception e) {
            log.error("Exception in RedisSubscriber", e);
        }
    }
}