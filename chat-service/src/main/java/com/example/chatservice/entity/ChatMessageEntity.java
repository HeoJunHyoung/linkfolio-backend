package com.example.chatservice.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "chat_message")
public class ChatMessageEntity {

    @Id
    private String id;

    @Indexed // roomId로 조회 성능 향상
    private String roomId;

    private Long senderId;

    private String content;

    private LocalDateTime createdAt;

    // 읽지 않은 사람 수 (아직 1:1 채팅이라 다음과 같이 정해질 듯)  [0:모두읽음, 1:상대안읽음]
    private int readCount;

    @Builder
    public ChatMessageEntity(String roomId, Long senderId, String content, LocalDateTime createdAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.readCount = 1; // 기본적으로 상대방은 안 읽음
    }
}