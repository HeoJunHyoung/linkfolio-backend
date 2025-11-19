package com.example.chatservice.dto;

import com.example.chatservice.entity.ChatMessageEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String id;
    private String roomId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
    private int readCount;

    public static ChatMessageResponse from(ChatMessageEntity entity) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .readCount(entity.getReadCount())
                .build();
    }
}