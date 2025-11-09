package com.example.authservice.service.kafka;

import com.example.authservice.config.KafkaTopics;
import com.example.authservice.dto.event.UserRegistrationRequestedEvent;
import com.example.authservice.exception.ErrorCode;
import com.example.commonmodule.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 회원가입(프로필 생성) 요청 이벤트를 발행
     */
    public void sendUserRegistrationRequested(UserRegistrationRequestedEvent event) {
        try {
            // KafkaTopics 사용
            kafkaTemplate.send(KafkaTopics.USER_REGISTRATION_REQUESTED, event);
            log.info("Kafka UserRegistrationRequestedEvent 발행 성공, UserId: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Kafka 이벤트 발행 실패 (SAGA 시작 실패), UserId: {}", event.getUserId(), e);
            // Kafka 발행 실패는 심각한 오류이므로 SAGA 트랜잭션 롤백을 트리거
            throw new BusinessException(ErrorCode.KAFKA_PRODUCE_FAILED);
        }
    }
}