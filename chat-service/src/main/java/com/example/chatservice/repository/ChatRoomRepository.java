package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatRoomEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoomEntity, String> {
    Optional<ChatRoomEntity> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    @Query(value = "{ $or: [ { 'user1Id': ?0 }, { 'user2Id': ?0 } ] }")
    Slice<ChatRoomEntity> findByUserId(Long userId, Pageable pageable);
}