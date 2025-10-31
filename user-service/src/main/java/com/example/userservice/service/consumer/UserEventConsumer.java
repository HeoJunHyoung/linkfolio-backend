package com.example.userservice.service.consumer;

import com.example.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserService userService; // 프로필 생성을 위임

    @KafkaListener(topics = "user-events", groupId = "user-profile-consumer")
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("수신된 사용자 생성 이벤트: userId={}", event.getUserId());
        try {
            userService.createUserProfile(event);
        } catch (Exception e) {
            log.error("사용자 프로필 생성 실패: userId={}", event.getUserId(), e);
            // TODO: 실패 처리 로직 (e.g., Dead Letter Queue)
        }
    }
}