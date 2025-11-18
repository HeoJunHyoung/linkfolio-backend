package com.example.authservice.service.kafka;

import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.entity.enumerate.AuthStatus;
import com.example.authservice.repository.AuthUserRepository;
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
public class AuthEventHandler {

    private final AuthUserRepository authUserRepository;

    /**
     * [SAGA 완료 & 데이터 동기화]
     * user-service(Debezium)의 Entity CDC 이벤트를 수신
     */
    @Transactional
    @KafkaListener(topics = "user_db_server.user_db.user_profile", groupId = "auth-saga-consumer")
    public void handleUserProfileEvent(ConsumerRecord<Object, Object> record) {
        // Value가 null이면(삭제 이벤트 등) 무시
        if (record.value() == null) return;

        GenericRecord value = (GenericRecord) record.value();

        // 1. Avro 데이터 파싱
        Long userId = (Long) value.get("user_id");
        // CharSequence -> String
        String status = value.get("status").toString();
        String name = value.get("name").toString();

        log.info("[CDC 수신] UserId: {}, Status: {}, Name: {}", userId, status, name);

        AuthUserEntity authUser = authUserRepository.findById(userId).orElse(null);
        if (authUser == null) return;

        // 2. SAGA 완료 처리: 상태가 'COMPLETED'이고 현재 'PENDING'이라면 가입 완료
        if ("COMPLETED".equals(status) && authUser.getStatus() == AuthStatus.PENDING) {
            authUser.updateStatus(AuthStatus.COMPLETED);
            log.info("SAGA 완료: AuthUser 상태 COMPLETED로 변경. UserId: {}", userId);
        }

        // 3. 데이터 동기화: 이름 변경 시 업데이트
        if (!name.equals(authUser.getName())) {
            authUser.updateName(name);
            log.info("데이터 동기화: 이름 변경 완료. UserId: {}", userId);
        }
    }
}