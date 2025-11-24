package com.example.communityservice.service.kafka;

import com.example.communityservice.entity.PostUserProfileEntity;
import com.example.communityservice.repository.PostUserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostUserEventHandler {

    private final PostUserProfileRepository userProfileRepository;

    @Transactional
    @KafkaListener(topics = "user_db_server.user_db.user_profile", groupId = "community-consumer-group")
    public void handleUserProfileUpdate(ConsumerRecord<Object, Object> record) {
        if (record.value() == null) return;

        try {
            GenericRecord value = (GenericRecord) record.value();

            Long userId = (Long) value.get("user_id");
            String name = value.get("name").toString();
            String email = value.get("email").toString();

            log.info("[CDC 수신] 사용자 프로필 동기화 - UserId: {}, Name: {}", userId, name);

            userProfileRepository.findById(userId)
                    .ifPresentOrElse(
                            existingUser -> existingUser.update(name, email),
                            () -> userProfileRepository.save(new PostUserProfileEntity(userId, name, email))
                    );

        } catch (Exception e) {
            log.error("[CDC 처리 실패] 메시지 처리 중 오류 발생", e);
        }
    }

}
