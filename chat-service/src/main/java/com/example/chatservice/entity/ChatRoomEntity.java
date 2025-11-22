package com.example.chatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Document(collection = "chat_room")
@CompoundIndex(def = "{'user1Id': 1, 'user2Id': 1}", unique = true) // 두 사용자 간의 채팅방은 유니크해야 함 (1:1 채팅)
@NoArgsConstructor
public class ChatRoomEntity {

    @Id
    private String id;

    private Long user1Id;
    private Long user2Id;

    private String lastMessage; // 채팅방 목록에서 보여지는 마지막 메시지 내용
    
    private LocalDateTime lastMessageTime; // 채팅방 목록이 최신 메시지 온 순서대로 정렬되는 기준
    
    private Map<String, LocalDateTime> lastReadAt = new HashMap<>(); // 각 사용자가 마지막으로 읽은 시간 Key: UserId , Value: LocalDateTime)

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
        if (this.lastReadAt == null) this.lastReadAt = new HashMap<>(); // 방어 로직
        this.lastReadAt.put(String.valueOf(userId), time);
    }

    public void increaseUnreadCount(Long userId) {
        if (this.unreadCounts == null) {
            this.unreadCounts = new HashMap<>();
        }
        this.unreadCounts.merge(String.valueOf(userId), 1, Integer::sum);
    }

    public void resetUnreadCount(Long userId) {
        if (this.unreadCounts == null) {
            this.unreadCounts = new HashMap<>();
        }
        this.unreadCounts.put(String.valueOf(userId), 0);
    }

    public int getUnreadCount(Long userId) {
        if (this.unreadCounts == null) {
            return 0;
        }
        return this.unreadCounts.getOrDefault(String.valueOf(userId), 0);
    }
}