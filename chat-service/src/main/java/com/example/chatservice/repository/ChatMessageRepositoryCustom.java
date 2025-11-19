package com.example.chatservice.repository;

import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

public interface ChatMessageRepositoryCustom {

    // 특정 방에서 상대방이 보낸 메시지 중, 내가 마지막으로 읽은 시점 이전에 발행된 메시지를 제외하고 읽음 처리
    // ㄴ ChatService 로직에 맞게, '내가 읽지 않은' 메시지에 대해 readAt 필드를 갱신
    Mono<Long> markMessagesAsRead(String roomId, Long senderId, LocalDateTime lastReadAt);
}