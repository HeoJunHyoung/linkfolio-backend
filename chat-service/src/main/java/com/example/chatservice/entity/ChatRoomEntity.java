package com.example.chatservice.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "chat_rooms")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@CompoundIndexes({
        // 1:1 채팅방의 고유성을 보장 (두 사용자 ID의 순서에 무관하게 유니크)
        @CompoundIndex(def = "{'user1Id': 1, 'user2Id': 1}", unique = true)
})
public class ChatRoomEntity {

    @Id
    private String id; // MongoDB의 고유 ID

    // 1:1 채팅 참여자 ID
    private Long user1Id;
    private Long user2Id;

    // 이메일 주소 (메타데이터용)
    private String user1Email;
    private String user2Email;

    // 채팅 목록 미리보기를 위한 메타데이터
    private String lastMessage; // 마지막 메시지 내용
    private LocalDateTime lastMessageTime; // 마지막 메시지 전송 시각

    // 각 사용자의 마지막 읽음 시각 (읽지 않은 메시지 카운트 계산용)
    private LocalDateTime user1LastReadAt;
    private LocalDateTime user2LastReadAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime lastModifiedAt;

    // 정적 팩토리 메서드
    public static ChatRoomEntity of(Long user1Id, Long user2Id, String user1Email, String user2Email) {
        // ID 순서 정렬 (CompoundIndex와 일관성 유지)
        if (user1Id.compareTo(user2Id) > 0) {
            Long tempId = user1Id;
            user1Id = user2Id;
            user2Id = tempId;
            String tempEmail = user1Email;
            user1Email = user2Email;
            user2Email = tempEmail;
        }

        return ChatRoomEntity.builder()
                .user1Id(user1Id)
                .user2Id(user2Id)
                .user1Email(user1Email)
                .user2Email(user2Email)
                .lastMessage("새 채팅방이 생성되었습니다.")
                .lastMessageTime(LocalDateTime.now())
                .user1LastReadAt(LocalDateTime.now())
                .user2LastReadAt(LocalDateTime.now())
                .build();
    }

    // 마지막 메시지 업데이트
    public void updateLastMessage(Long senderId, String message) {
        this.lastMessage = message;
        this.lastMessageTime = LocalDateTime.now();

        // 메시지를 보낸 사용자 ID가 user1Id라면 user1LastReadAt을 갱신 (자기가 보낸 메시지는 읽은 것으로 간주)
        if (this.user1Id.equals(senderId)) {
            this.user1LastReadAt = LocalDateTime.now();
        } else if (this.user2Id.equals(senderId)) {
            this.user2LastReadAt = LocalDateTime.now();
        }
    }

    // 사용자의 마지막 읽음 시각 업데이트
    public void updateLastReadAt(Long userId, LocalDateTime lastReadAt) {
        if (this.user1Id.equals(userId)) {
            this.user1LastReadAt = lastReadAt;
        } else if (this.user2Id.equals(userId)) {
            this.user2LastReadAt = lastReadAt;
        }
    }

    // 상대방 ID 조회
    public Long getOtherUserId(Long myUserId) {
        return myUserId.equals(this.user1Id) ? this.user2Id : this.user1Id;
    }

    // 나의 마지막 읽음 시각 조회
    public LocalDateTime getMyLastReadAt(Long myUserId) {
        return myUserId.equals(this.user1Id) ? this.user1LastReadAt : this.user2LastReadAt;
    }
}