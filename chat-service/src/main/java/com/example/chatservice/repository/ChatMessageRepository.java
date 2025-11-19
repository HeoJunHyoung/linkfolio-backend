package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatMessageEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessageEntity, String>, ChatMessageRepositoryCustom {

    // 특정 방의 메시지를 최신순으로 조회
    Flux<ChatMessageEntity> findByRoomIdOrderByCreatedAtDesc(String roomId);
}