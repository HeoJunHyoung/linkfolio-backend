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

    @Indexed
    private String roomId;

    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
    private int readCount;

    @Builder
    public ChatMessageEntity(String roomId, Long senderId, String content, LocalDateTime createdAt) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.readCount = 1;
    }
}