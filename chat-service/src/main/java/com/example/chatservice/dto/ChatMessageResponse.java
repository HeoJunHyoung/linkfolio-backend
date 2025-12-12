package com.example.chatservice.dto;

import com.example.chatservice.dto.enumerate.MessageType;
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
public class ChatMessageResponse { // 채팅 내용 DTO
    private MessageType type;
    private String id;
    private String roomId;
    private Long senderId;
    private Long receiverId;
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