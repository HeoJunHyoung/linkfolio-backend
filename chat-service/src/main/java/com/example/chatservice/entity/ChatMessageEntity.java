package com.example.chatservice.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "chat_messages")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageEntity {

    @Id
    private String id; // MongoDB의 고유 ID

    @Indexed
    private String roomId; // 채팅방 ID (ChatRoomEntity의 ID)

    private Long senderId;

    private String content; // 메시지 내용

    // 읽음 처리: 메시지를 수신자가 읽은 시각. null이면 아직 안 읽은 것임
    private LocalDateTime readAt;

    @CreatedDate
    private LocalDateTime createdAt;

    public static ChatMessageEntity of(String roomId, Long senderId, String content) {
        return ChatMessageEntity.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .build();
    }

    public void markAsRead() {
        this.readAt = LocalDateTime.now();
    }
}