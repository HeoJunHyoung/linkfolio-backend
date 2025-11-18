package com.example.userservice.service.kafka;

import com.example.commonmodule.dto.event.UserRegistrationRequestedEvent;
import com.example.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventHandler {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    // 토픽명: outbox.event.{이벤트타입}
    @KafkaListener(topics = "outbox.event.UserRegistrationRequestedEvent", groupId = "user-profile-consumer")
    public void handleUserRegistrationRequested(ConsumerRecord<Object, Object> record) {
        try {
            // 1. Avro 메시지에서 payload(JSON 문자열) 추출
            String payloadJson;
            if (record.value() instanceof GenericRecord) {
                GenericRecord genericRecord = (GenericRecord) record.value();
                // Outbox SMT 설정에 따라서 payload 필드에 JSON이 들어있음
                payloadJson = genericRecord.get("payload").toString();
            } else {
                // 혹시 String으로 올 경우 대비
                payloadJson = record.value().toString();
            }

            log.info("[Outbox 수신] Payload: {}", payloadJson);

            // 2. JSON -> DTO 변환
            UserRegistrationRequestedEvent event = objectMapper.readValue(payloadJson, UserRegistrationRequestedEvent.class);

            // 3. 프로필 생성 로직 실행
            // (여기서 DB에 저장되면, user-profile-connector가 이벤트를 자동 발행함)
            userService.createUserProfile(event);

        } catch (Exception e) {
            log.error("프로필 생성 처리 중 오류 발생", e);
        }
    }
}