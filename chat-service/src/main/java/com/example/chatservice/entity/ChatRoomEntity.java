package com.example.chatservice.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Document(collection = "chat_room")
@CompoundIndex(def = "{'user1Id': 1, 'user2Id': 1}", unique = true) // 두 사용자 간의 채팅방은 유니크해야 함 (1:1 채팅)
public class ChatRoomEntity {

    @Id
    private String id;

    private Long user1Id;
    private Long user2Id;

    private String lastMessage;
    private LocalDateTime lastMessageTime;

    // 각 사용자가 마지막으로 읽은 시간 (Unread Count 계산용)
    // Key: UserId (String), Value: LocalDateTime
    private Map<String, LocalDateTime> lastReadAt = new HashMap<>();

    @Builder
    public ChatRoomEntity(Long user1Id, Long user2Id) {
        // 항상 작은 ID가 user1Id가 되도록 정렬하여 중복 방지
        if (user1Id < user2Id) {
            this.user1Id = user1Id;
            this.user2Id = user2Id;
        } else {
            this.user1Id = user2Id;
            this.user2Id = user1Id;
        }
        this.lastMessageTime = LocalDateTime.now();
        this.lastReadAt.put(String.valueOf(user1Id), LocalDateTime.now());
        this.lastReadAt.put(String.valueOf(user2Id), LocalDateTime.now());
    }

    public void updateLastMessage(String message, LocalDateTime time) {
        this.lastMessage = message;
        this.lastMessageTime = time;
    }

    public void updateReadTime(Long userId, LocalDateTime time) {
        this.lastReadAt.put(String.valueOf(userId), time);
    }
}