package com.example.chatservice.dto.response;

import com.example.chatservice.entity.ChatRoomEntity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomListResponse {
    private String roomId;
    private Long partnerId;
    private String partnerEmail;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount; // 개별 배지

    public static ChatRoomListResponse fromEntity(ChatRoomEntity entity, Long myUserId, Integer unreadCount) {
        Long partnerId = entity.getOtherUserId(myUserId);
        String partnerEmail = myUserId.equals(entity.getUser1Id()) ? entity.getUser2Email() : entity.getUser1Email();

        return ChatRoomListResponse.builder()
                .roomId(entity.getId())
                .partnerId(partnerId)
                .partnerEmail(partnerEmail)
                .lastMessage(entity.getLastMessage())
                .lastMessageTime(entity.getLastMessageTime())
                .unreadCount(unreadCount)
                .build();
    }
}