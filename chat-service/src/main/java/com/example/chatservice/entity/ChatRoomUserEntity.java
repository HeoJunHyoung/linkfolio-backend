package com.example.chatservice.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;


// 사용자별 상태 및 안 읽은 카운트
@Getter
@Document(collection = "chat_user")
@CompoundIndex(def = "{'userId': 1, 'roomId': 1}", unique = true) // 한 유저가 한 방에 중복으로 들어갈 수 없음 (유니크 제약)
@NoArgsConstructor
public class ChatRoomUserEntity {

    @Id
    private String id;

    @Indexed
    private String roomId;
    @Indexed
    private Long userId;

    private String roomName;

    // == 상태 관리 ==
    private boolean isActive;          // 현재 참여 중인지 (나가기 시 false)
    private LocalDateTime visibleFrom; // 재입장 시 이 시간 이후 메시지만 조회
    private LocalDateTime lastReadAt;  // 마지막 읽은 시간

    // Atomic Update 대상 (MongoDB에 저장됨)
    private int unreadCount;

    @Builder
    public ChatRoomUserEntity(String roomId, Long userId) {
        this.roomId = roomId;
        this.userId = userId;
        this.isActive = true;
        this.visibleFrom = LocalDateTime.now();
        this.lastReadAt = LocalDateTime.now();
        this.unreadCount = 0;
    }

    // 재입장 처리
    public void rejoin() {
        this.isActive = true;
        this.visibleFrom = LocalDateTime.now(); // 나간 사이의 대화는 숨김
        this.unreadCount = 0;
    }

    // 나가기 처리
    public void leave() {
        this.isActive = false;
        this.unreadCount = 0;
    }

    // 읽음 처리
    public void resetUnreadCount() {
        this.unreadCount = 0;
        this.lastReadAt = LocalDateTime.now();
    }
}