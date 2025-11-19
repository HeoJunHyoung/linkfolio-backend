package com.example.chatservice.dto.response;

import com.example.chatservice.entity.ChatRoomEntity;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ChatRoomDetailResponse {
    private String roomId;
    private Long myUserId;
    private Long partnerId;
    private String partnerEmail;
    private List<ChatMessageResponse> messages;

    public static ChatRoomDetailResponse from(ChatRoomEntity room, Long myUserId, List<ChatMessageResponse> messages) {
        Long partnerId = room.getOtherUserId(myUserId);
        String partnerEmail = myUserId.equals(room.getUser1Id()) ? room.getUser2Email() : room.getUser1Email();

        return ChatRoomDetailResponse.builder()
                .roomId(room.getId())
                .myUserId(myUserId)
                .partnerId(partnerId)
                .partnerEmail(partnerEmail)
                .messages(messages)
                .build();
    }
}