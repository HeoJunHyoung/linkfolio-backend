package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatMessageEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Long> markMessagesAsRead(String roomId, Long senderId, LocalDateTime lastReadAt) {

        // 1. 쿼리 조건:
        //    - roomId가 일치
        //    - senderId가 상대방 (내가 받은 메시지)
        //    - readAt 필드가 null (아직 안 읽은 메시지)
        //    - createdAt이 내가 마지막으로 읽은 시각(lastReadAt) 이후

        Query query = new Query(Criteria.where("roomId").is(roomId)
                .and("senderId").is(senderId)
                .and("readAt").isNull()
                .and("createdAt").gt(lastReadAt)); // lastReadAt 이후에 전송된 메시지

        Update update = new Update().set("readAt", LocalDateTime.now());

        // updateMulti를 사용하여 여러 문서를 업데이트하고, 업데이트된 문서 수를 반환
        return mongoTemplate.updateMulti(query, update, ChatMessageEntity.class)
                .map(result -> result.getModifiedCount());
    }
}