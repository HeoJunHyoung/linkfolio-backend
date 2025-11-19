package com.example.chatservice.service.kafka;

import com.example.chatservice.entity.ChatUserProfileEntity;
import com.example.chatservice.repository.ChatUserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatUserProfileEventHandler {

    private final ChatUserProfileRepository chatUserProfileRepository;

    // Debezium이 발행하는 user_profile 변경 토픽 구독
    @KafkaListener(topics = "user_db_server.user_db.user_profile", groupId = "chat-service-consumer-group")
    public void handleUserProfileUpdate(ConsumerRecord<Object, Object> record) {
        if (record.value() == null) return; // 삭제 이벤트 무시

        try {
            GenericRecord value = (GenericRecord) record.value();

            Long userId = (Long) value.get("user_id");
            String name = value.get("name").toString();

            log.info("[CDC] 사용자 프로필 동기화 - UserId: {}, Name: {}", userId, name);

            // 로컬 MongoDB에 저장 (Upsert)
            ChatUserProfileEntity profile = ChatUserProfileEntity.builder()
                    .userId(userId)
                    .name(name)
                    .build();

            chatUserProfileRepository.save(profile);

        } catch (Exception e) {
            log.error("프로필 동기화 실패", e);
        }
    }
}