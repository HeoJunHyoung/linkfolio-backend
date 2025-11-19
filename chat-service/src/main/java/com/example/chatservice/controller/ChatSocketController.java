package com.example.chatservice.controller;

import com.example.chatservice.dto.ChatMessageRequest;
import com.example.chatservice.service.ChatService;
import com.example.chatservice.service.OnlineStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatSocketController { // STOMP 메시지 핸들링

    private final ChatService chatService;
    private final OnlineStatusService onlineStatusService;

    // 메시지 전송 엔드포인트: /app/chat/send
    @MessageMapping("/chat/send")
    public void sendMessage(@Payload ChatMessageRequest request, Principal principal) {
        Long senderId = Long.parseLong(principal.getName()); // StompHandler에서 설정한 ID
        chatService.sendMessage(senderId, request);
    }

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        if (event.getUser() != null) {
            Long userId = Long.parseLong(event.getUser().getName());
            onlineStatusService.setUserOnline(userId);
            log.info("User Connected: {}", userId);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() != null) {
            Long userId = Long.parseLong(event.getUser().getName());
            onlineStatusService.setUserOffline(userId);
            log.info("User Disconnected: {}", userId);
        }
    }
}