package com.example.chatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Document(collection = "chat_room")
@NoArgsConstructor
public class ChatRoomEntity {

    @Id
    private String id;

    // 1:1 채팅방 중복 생성 방지용 (그룹 채팅 시 null 가능)
    private Long user1Id;
    private Long user2Id;

    private String lastMessage;
    private LocalDateTime lastMessageTime;

    @Builder
    public ChatRoomEntity(Long user1Id, Long user2Id) {
        // 1:1 채팅일 경우 ID 정렬하여 저장 (A-B방과 B-A방은 같은 방임)
        if (user1Id != null && user2Id != null) {
            this.user1Id = Math.min(user1Id, user2Id);
            this.user2Id = Math.max(user1Id, user2Id);
        }
        this.lastMessageTime = LocalDateTime.now();
    }

    public void updateLastMessage(String message, LocalDateTime time) {
        this.lastMessage = message;
        this.lastMessageTime = time;
    }
}