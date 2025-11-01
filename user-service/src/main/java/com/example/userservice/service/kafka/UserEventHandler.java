package com.example.userservice.service.kafka;

import com.example.userservice.config.KafkaTopics;
import com.example.userservice.dto.event.UserProfileCreationFailureEvent;
import com.example.userservice.dto.event.UserProfileCreationSuccessEvent;
import com.example.userservice.dto.event.UserRegistrationRequestedEvent;
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

    // SAGA 응답(성공/실패)을 보내기 위한 KafkaTemplate
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * SAGA 시작 이벤트 수신
     * @param event 프로필 생성을 위한 사용자 정보
     */
    @KafkaListener(topics = KafkaTopics.USER_REGISTRATION_REQUESTED, groupId = "user-profile-consumer")
    public void handleUserRegistrationRequested(UserRegistrationRequestedEvent event) {
        log.info("수신된 사용자 생성 요청 이벤트: userId={}", event.getUserId());

        try {
            // 1. 핵심 로직(프로필 생성) 호출
            // (UserService.createUserProfile 내부는 @Transactional로 묶여있음)
            userService.createUserProfile(event);

            // 2. [성공] SAGA 성공 이벤트를 auth-service로 다시 발행
            UserProfileCreationSuccessEvent successEvent = new UserProfileCreationSuccessEvent(event.getUserId());
            kafkaTemplate.send(KafkaTopics.USER_PROFILE_CREATED_SUCCESS, successEvent);
            log.info("프로필 생성 성공. SAGA Success 이벤트 발행. UserId: {}", event.getUserId());

        } catch (Exception e) {
            // 3. [실패] SAGA 보상(실패) 이벤트를 auth-service로 발행
            log.error("사용자 프로필 생성 실패: userId={}. SAGA Failure(보상) 이벤트 발행.", event.getUserId(), e);

            UserProfileCreationFailureEvent failureEvent = new UserProfileCreationFailureEvent(
                    event.getUserId(),
                    e.getMessage() // DB 제약조건 위반 메시지 등
            );
            kafkaTemplate.send(KafkaTopics.USER_PROFILE_CREATED_FAILURE, failureEvent);
        }
    }

}