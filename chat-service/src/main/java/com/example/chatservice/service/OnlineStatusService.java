package com.example.chatservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OnlineStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STATUS_KEY_PREFIX = "STATUS:";

    public void setUserOnline(Long userId) {
        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + userId, "ONLINE");
    }

    public void setUserOffline(Long userId) {
        redisTemplate.delete(STATUS_KEY_PREFIX + userId);
    }

    public boolean isUserOnline(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(STATUS_KEY_PREFIX + userId));
    }
}