package com.example.chatservice.controller;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ChatRoomResponse;
import com.example.chatservice.service.ChatService;
import com.example.commonmodule.dto.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    // 채팅방 목록 조회
    @GetMapping("/rooms")
    public ResponseEntity<Slice<ChatRoomResponse>> getMyRooms(@AuthenticationPrincipal AuthUser authUser,
                                                              @PageableDefault(size = 20, sort = "lastMessageTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chatService.getMyChatRooms(authUser.getUserId(), pageable));
    }

    // 메시지 내역 조회
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Slice<ChatMessageResponse>> getMessages(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PathVariable String roomId,
                                                                  @RequestParam(defaultValue = "0") int page) {
        // userId를 서비스로 전달 (visibleFrom 체크용)
        return ResponseEntity.ok(chatService.getChatMessages(roomId, authUser.getUserId(), page, 20));
    }

    // 채팅방 나가기
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> leaveRoom(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable String roomId) {
        chatService.leaveChatRoom(authUser.getUserId(), roomId);
        return ResponseEntity.ok().build();
    }

    // 전체 안 읽은 개수
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getTotalUnreadCount(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(chatService.getTotalUnreadCount(authUser.getUserId()));
    }
}