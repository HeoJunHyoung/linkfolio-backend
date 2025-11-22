package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatRoomUserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ChatRoomUserRepository extends MongoRepository<ChatRoomUserEntity, String> {

    // 내 '활성화된' 채팅방 목록 조회
    Slice<ChatRoomUserEntity> findByUserIdAndIsActiveTrue(Long userId, Pageable pageable);

    // 특정 방의 내 정보 조회
    Optional<ChatRoomUserEntity> findByRoomIdAndUserId(String roomId, Long userId);
}