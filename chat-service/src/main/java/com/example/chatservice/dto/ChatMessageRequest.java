package com.example.chatservice.dto;

import com.example.chatservice.dto.enumerate.MessageType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatMessageRequest {
    private MessageType type;
    private Long receiverId; // 1:1 채팅 상대방 ID
    private String content;
    private String roomId;

    @Builder
    public ChatMessageRequest(MessageType type, Long receiverId, String content, String roomId) {
        this.type = type;
        this.receiverId = receiverId;
        this.content = content;
        this.roomId = roomId;
    }
}