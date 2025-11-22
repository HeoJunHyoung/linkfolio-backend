package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatRoomEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoomEntity, String> {
    Optional<ChatRoomEntity> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);
}