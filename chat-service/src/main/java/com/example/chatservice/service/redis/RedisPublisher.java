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
    private final ChannelTopic topic;

    public void publish(ChannelTopic topic, ChatMessageResponse message) {
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}