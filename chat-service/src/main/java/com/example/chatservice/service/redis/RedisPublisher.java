package com.example.chatservice.service.redis;

import com.example.chatservice.dto.ChatMessageResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisPublisher {
    private final RedisTemplate<String, Object> redisTemplate;

    @Getter
    private final ChannelTopic topic; // topic: chatroom

    // 1. RedisPublisher는 Redis Server에게 "이 메시지 뿌려줘" 요청한다.
    // 2. Redis Server는 chatroom 구독하고 있는 모든 서버(chat pods)에게 이벤트 전송
    // 3. 각 서버(chat pods)들의 RedisMessageListenerContainer가 이 신호를 감지하고, 등록된 리스너인 RedisSubscriber의 sendMessage() 메서드를 실행
    public void publish(ChannelTopic topic, ChatMessageResponse message) {
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}