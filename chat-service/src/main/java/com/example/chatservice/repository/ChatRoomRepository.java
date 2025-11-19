package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatRoomEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ChatRoomRepository extends ReactiveMongoRepository<ChatRoomEntity, String> {

    // 특정 사용자(userId)가 참여하는 모든 채팅방을 조회
    // 쿼리: user1Id 또는 user2Id가 userId와 일치
    @Query("{ '$or': [ { 'user1Id': ?0 }, { 'user2Id': ?0 } ] }")
    Flux<ChatRoomEntity> findMyRooms(Long userId);

    // 두 사용자 ID로 채팅방을 조회 (Lazy Creation 시 중복 확인용)
    Flux<ChatRoomEntity> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);
}