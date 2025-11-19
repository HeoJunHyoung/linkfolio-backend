package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;

public interface ChatMessageRepository extends MongoRepository<ChatMessageEntity, String> {
    Slice<ChatMessageEntity> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    // 읽지 않은 메시지 개수 계산 (나보다 늦게 생성된 메시지 중 내가 보낸 게 아닌 것)
    @Query(value = "{ 'roomId': ?0, 'createdAt': { $gt: ?1 }, 'senderId': { $ne: ?2 } }", count = true)
    long countUnreadMessages(String roomId, LocalDateTime lastReadAt, Long myUserId);
}