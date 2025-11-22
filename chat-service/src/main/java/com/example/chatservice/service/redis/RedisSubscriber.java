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
     * [RedisSubscriber의 역할과 흐름]
     * Redis에서 메시지가 발행(publish)되면, 대기하고 있던 onMessage(sendMessage)가 해당 메시지를 수신하여 처리한다.
     *
     * [프론트엔드와의 구독(Subscribe) 관계]
     * 1. 프론트엔드는 채팅방 입장 시점 등에 맞춰 필요한 STOMP 토픽을 미리 구독한다.
     * 2. 서버(RedisSubscriber)가 아래 주소로 메시지를 전송(convertAndSend)하면,
     *    스프링 내부 브로커(SimpleBroker)가 해당 토픽을 구독 중인 모든 클라이언트에게 메시지를 실시간으로 분배한다.
     *
     * * [구독 토픽 예시]
     * - /topic/chat/{roomId} : 일반 대화 수신용 (채팅방 내부 사용자들)
     * - /topic/chat/{roomId}/read : 실시간 읽음 처리 반영용 (메시지 옆 숫자 1 지우기)
     * - /topic/chat/{roomId}/typing : "상대방이 입력 중입니다..." 표시용
     *
     * [전체 흐름]
     * 1. 유저가 메시지 전송 → 서버가 받아서 Redis chatroom 채널에 던짐. ("야! 이거 모든 서버가 다 받아봐!")
     * 2. 다른 서버가 Redis chatroom 채널에서 메시지 수신. ("어? 물건 왔네?")
     * 3. 내용을 까보니 1번 방 메시지임 → STOMP /topic/chat/1 주소로 쏨. ("1번 방 구독하고 있는 손님들한테 배달해!")
     * -> RedisConfig의 chatroom은 서버끼리의 통신 채널명이고, Subscriber의 /topic/chat은 클라이언트에게 쏘는 최종 주소
     */
    public void sendMessage(String publishMessage) {
        try {
            ChatMessageResponse message = objectMapper.readValue(publishMessage, ChatMessageResponse.class);

            if (message.getType() == MessageType.READ) {
                messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId() + "/read", message); // 읽음 표시
            } else if (message.getType() == MessageType.TYPING) {
                messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId() + "/typing", message); // 입력중
            } else {
                messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), message);     // [1] 채팅방 내부용 (실시간 대화)
                messagingTemplate.convertAndSend("/topic/user/" + message.getReceiverId(), message); // [2] 채팅방 외부/개인용 (알림, 안 읽은 개수, 채팅 목록 실시간 갱신)
            }

        } catch (Exception e) {
            log.error("Exception in RedisSubscriber", e);
        }
    }
}