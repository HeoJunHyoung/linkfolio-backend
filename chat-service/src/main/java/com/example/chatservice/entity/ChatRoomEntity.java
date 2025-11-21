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

    // 각 사용자가 마지막으로 읽은 시간 Key: UserId , Value: LocalDateTime)
    private Map<String, LocalDateTime> lastReadAt = new HashMap<>();

    private Map<String, Integer> unreadCounts = new HashMap<>(); // 사용자 ID별 안 읽은 메시지 개수 (Key: UserId, Value: Count)

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

        String u1 = String.valueOf(user1Id);
        String u2 = String.valueOf(user2Id);

        this.lastReadAt.put(u1, LocalDateTime.now());
        this.lastReadAt.put(u2, LocalDateTime.now());

        this.unreadCounts.put(u1, 0);
        this.unreadCounts.put(u2, 0);
    }

    public void updateLastMessage(String message, LocalDateTime time) {
        this.lastMessage = message;
        this.lastMessageTime = time;
    }

    public void updateReadTime(Long userId, LocalDateTime time) {
        this.lastReadAt.put(String.valueOf(userId), time);
    }

    public void increaseUnreadCount(Long userId) {
        this.unreadCounts.merge(String.valueOf(userId), 1, Integer::sum);
    }

    public void resetUnreadCount(Long userId) {
        this.unreadCounts.put(String.valueOf(userId), 0);
    }

    public int getUnreadCount(Long userId) {
        return this.unreadCounts.getOrDefault(String.valueOf(userId), 0);
    }
}