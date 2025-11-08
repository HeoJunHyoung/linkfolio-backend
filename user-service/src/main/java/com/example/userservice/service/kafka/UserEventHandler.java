package com.example.userservice.service.kafka;

import com.example.userservice.config.KafkaTopics;
import com.example.userservice.dto.event.UserProfileCreationFailureEvent;
import com.example.userservice.dto.event.UserProfileCreationSuccessEvent;
import com.example.userservice.dto.event.UserProfilePublishedEvent;
import com.example.userservice.dto.event.UserRegistrationRequestedEvent;
import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventHandler {

    private final UserService userService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.USER_REGISTRATION_REQUESTED, groupId = "user-profile-consumer")
    public void handleUserRegistrationRequested(UserRegistrationRequestedEvent event) {
        log.info("수신된 사용자 생성 요청 이벤트: userId={}", event.getUserId());

        try {
            // 1. 핵심 로직 호출 (이제 UserProfileEntity를 반환)
            UserProfileEntity savedProfile = userService.createUserProfile(event);

            // --- [!! Fan-out 시작 !!] ---
            // 2-A. [SAGA용] SAGA 성공 이벤트를 auth-service로 다시 발행
            UserProfileCreationSuccessEvent successEvent = new UserProfileCreationSuccessEvent(event.getUserId());
            kafkaTemplate.send(KafkaTopics.USER_PROFILE_CREATED_SUCCESS, successEvent);
            log.info("프로필 생성 성공. SAGA Success 이벤트 발행. UserId: {}", event.getUserId());

            // 2-B. [전파용] 데이터 동기화 이벤트를 portfolio-service (및 기타)로 발행
            UserProfilePublishedEvent publishedEvent = UserProfilePublishedEvent.fromEntity(savedProfile);
            kafkaTemplate.send(KafkaTopics.USER_PROFILE_UPDATED, publishedEvent);
            log.info("프로필 생성 성공. 데이터 전파(Fan-out) 이벤트 발행. UserId: {}", event.getUserId());

            // --- [!! Fan-out 종료 !!] ---

        } catch (Exception e) {
            // 3. [실패] SAGA 보상(실패) 이벤트를 auth-service로 발행
            log.error("사용자 프로필 생성 실패: userId={}. SAGA Failure(보상) 이벤트 발행.", event.getUserId(), e);

            UserProfileCreationFailureEvent failureEvent = new UserProfileCreationFailureEvent(
                    event.getUserId(),
                    e.getMessage()
            );
            kafkaTemplate.send(KafkaTopics.USER_PROFILE_CREATED_FAILURE, failureEvent);
        }
    }
}