package com.example.authservice.service.kafka;

import com.example.authservice.config.KafkaTopics;
import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.entity.enumerate.AuthStatus;
import com.example.authservice.exception.ErrorCode;
import com.example.authservice.repository.AuthUserRepository;
import com.example.commonmodule.dto.event.UserProfileCreationFailureEvent;
import com.example.commonmodule.dto.event.UserProfileCreationSuccessEvent;
import com.example.commonmodule.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEventHandler {

    private final AuthUserRepository authUserRepository;

    /**
     * [SAGA Success] 프로필 생성 성공 이벤트 수신
     * AuthUser의 상태를 PENDING -> COMPLETED로 변경
     */
    @Transactional
    @KafkaListener(topics = KafkaTopics.USER_PROFILE_CREATED_SUCCESS, groupId = "auth-consumer-group")
    public void handleProfileCreationSuccess(UserProfileCreationSuccessEvent event) {
        log.info("[SAGA Success] 프로필 생성 성공 이벤트 수신. UserId: {}", event.getUserId());

        AuthUserEntity authUser = authUserRepository.findById(event.getUserId())
                .orElseThrow(() -> {
                    log.error("SAGA 오류: 성공 이벤트를 받았으나 AuthUser 없음. UserId: {}", event.getUserId());
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });

        if (authUser.getStatus() == AuthStatus.PENDING) {
            authUser.updateStatus(AuthStatus.COMPLETED);
            authUserRepository.save(authUser);
            log.info("AuthUser 상태 COMPLETED로 변경 완료. UserId: {}", event.getUserId());
        } else {
            log.warn("AuthUser 상태가 PENDING이 아님 (이미 처리됨?). Status: {}, UserId: {}",
                    authUser.getStatus(), event.getUserId());
        }
    }

    /**
     * [SAGA Compensation] 프로필 생성 실패(보상) 이벤트 수신
     * AuthUser의 상태를 PENDING -> CANCELLED로 변경
     */
    @Transactional
    @KafkaListener(topics = KafkaTopics.USER_PROFILE_CREATED_FAILURE, groupId = "auth-consumer-group")
    public void handleProfileCreationFailure(UserProfileCreationFailureEvent event) {
        log.error("[SAGA Compensation] 프로필 생성 실패 이벤트 수신. UserId: {}, Reason: {}",
                event.getUserId(), event.getReason());

        AuthUserEntity authUser = authUserRepository.findById(event.getUserId())
                .orElseThrow(() -> {
                    log.error("SAGA 오류: 실패 이벤트를 받았으나 AuthUser 없음. UserId: {}", event.getUserId());
                    return new BusinessException(ErrorCode.USER_NOT_FOUND);
                });

        if (authUser.getStatus() == AuthStatus.PENDING) {
            // [중요] SAGA 롤백: 상태를 CANCELLED로 변경
            authUser.updateStatus(AuthStatus.CANCELLED);
            authUserRepository.save(authUser);
            log.info("AuthUser 상태 CANCELLED로 변경 완료 (보상 트랜잭션). UserId: {}", event.getUserId());
        } else {
            log.warn("AuthUser 상태가 PENDING이 아님 (이미 처리됨?). Status: {}, UserId: {}",
                    authUser.getStatus(), event.getUserId());
        }
    }
}