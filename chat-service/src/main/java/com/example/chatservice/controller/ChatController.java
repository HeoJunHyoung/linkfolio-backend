package com.example.chatservice.controller;

import com.example.chatservice.dto.response.ChatRoomDetailResponse;
import com.example.chatservice.dto.response.ChatRoomListResponse;
import com.example.chatservice.service.ChatService;
import com.example.commonmodule.dto.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chat-service/rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "내 채팅방 목록 조회", description = "사용자가 참여 중인 모든 채팅방 목록을 최신 메시지 순으로 조회하고, 개별 방의 읽지 않은 메시지 수를 포함합니다.")
    @GetMapping
    public Flux<ChatRoomListResponse> getMyChatRooms(@AuthenticationPrincipal AuthUser authUser) {
        // 1. 참여 중인 방 목록을 가져옴
        return chatService.getMyChatRooms(authUser.getUserId())
                // 2. 각 방에 대해 읽지 않은 메시지 수(개별 배지)를 Redis에서 조회하여 응답 DTO로 변환
                .flatMap(chatRoomEntity ->
                        chatService.getUnreadCount(chatRoomEntity.getId(), authUser.getUserId())
                                .map(unreadCount -> ChatRoomListResponse.fromEntity(chatRoomEntity, authUser.getUserId(), unreadCount.intValue()))
                );
    }

    @Operation(summary = "상대방 ID로 채팅방 조회 또는 생성", description = "상대방 ID를 기반으로 1:1 채팅방을 조회하거나, 없으면 생성하고 방 정보 및 히스토리를 반환합니다. (Lazy Creation 시작)")
    @GetMapping("/partner/{partnerId}")
    public Mono<ChatRoomDetailResponse> getOrCreateChatRoom(@PathVariable Long partnerId, @AuthenticationPrincipal AuthUser authUser) {
        // 채팅방을 조회하거나 생성
        return chatService.getOrCreateChatRoom(authUser.getUserId(), partnerId)
                // 생성된 방의 실제 ID를 사용하여 상세 정보와 메시지 히스토리를 조회
                .flatMap(room -> chatService.getChatRoomDetailWithMessages(room.getId(), authUser.getUserId()));
    }

    @Operation(summary = "특정 채팅방 상세 정보 및 메시지 기록 조회", description = "채팅방 ID로 메시지 기록을 조회합니다.")
    @GetMapping("/{roomId}")
    public Mono<ChatRoomDetailResponse> getChatRoomDetail(@PathVariable String roomId, @AuthenticationPrincipal AuthUser authUser) {
        return chatService.getChatRoomDetailWithMessages(roomId, authUser.getUserId());
    }

    @Operation(summary = "총 읽지 않은 메시지 수 조회", description = "모든 채팅방의 읽지 않은 메시지 총합을 반환합니다. (총합 배지)")
    @GetMapping("/unread/sum")
    public Mono<Long> getUnreadMessageSum(@AuthenticationPrincipal AuthUser authUser) {
        return chatService.getUnreadCountSum(authUser.getUserId());
    }
}