package com.example.chatservice.dto;

import com.example.chatservice.entity.ChatMessageEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// WebSocket을 통해 클라이언트와 서버가 주고받을 메시지 규격
@Data
@Builder
public class ChatMessageDto {
    public enum MessageType {
        ENTER, // 채팅방 입장/접속
        TALK,  // 일반 메시지
        READ,  // 메시지 읽음 확인
        TYPING, // 입력 중 상태
        EXIT, // 채팅방 퇴장/접속 종료
        ROOM_UPDATE // 채팅방 목록 갱신용 (Pub/Sub)
    }

    private MessageType type;
    private String roomId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    // DTO 변환
    public static ChatMessageDto fromEntity(ChatMessageEntity entity) {
        return ChatMessageDto.builder()
                .type(MessageType.TALK) // DB에서 로드된 메시지는 TALK 타입
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .build();
    }
}