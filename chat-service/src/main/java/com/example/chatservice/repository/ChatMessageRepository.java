package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;

public interface ChatMessageRepository extends MongoRepository<ChatMessageEntity, String> {

    // 재입장 시 visibleFrom 이후 메시지만 조회
    @Query("{ 'roomId': ?0, 'createdAt': { $gt: ?1 } }")
    Slice<ChatMessageEntity> findAllByRoomIdAndCreatedAtAfter(String roomId, LocalDateTime visibleFrom, Pageable pageable);
}