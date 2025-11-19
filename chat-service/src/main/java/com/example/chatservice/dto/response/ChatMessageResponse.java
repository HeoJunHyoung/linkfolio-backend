package com.example.chatservice.dto.response;

import com.example.chatservice.entity.ChatMessageEntity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private String messageId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private boolean isSystemMessage; // 날짜 구분선 등의 시스템 메시지 여부

    public static ChatMessageResponse fromEntity(ChatMessageEntity entity) {
        return ChatMessageResponse.builder()
                .messageId(entity.getId())
                .senderId(entity.getSenderId())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .isSystemMessage(false)
                .build();
    }

    // 날짜 구분선 생성용
    public static ChatMessageResponse createDateDivider(String dateContent) {
        return ChatMessageResponse.builder()
                .content(dateContent)
                .isSystemMessage(true)
                .build();
    }
}