package com.example.chatservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatMessageRequest {
    private Long receiverId; // 1:1 채팅 상대방 ID
    private String content;
}