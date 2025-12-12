package com.example.chatservice.repository;

import com.example.chatservice.entity.ChatUserProfileEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatUserProfileRepository extends MongoRepository<ChatUserProfileEntity, Long> {
    // 기본 제공되는 findAllById(Iterable<Long> ids)를 사용하여 Batch 조회 가능
}