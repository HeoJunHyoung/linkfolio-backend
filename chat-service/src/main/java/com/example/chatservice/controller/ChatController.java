package com.example.chatservice.controller;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ChatRoomResponse;
import com.example.chatservice.service.ChatService;
import com.example.commonmodule.dto.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    // 채팅방 목록 조회
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(@AuthenticationPrincipal Object principal) {
        if (principal instanceof AuthUser authUser) {
            return ResponseEntity.ok(chatService.getMyChatRooms(authUser.getUserId()));
        }
        // Principal이 AuthUser가 아닐 경우 (예외 처리)
        return ResponseEntity.badRequest().build();
    }

    // 채팅방 메시지 내용 조회 (무한스크롤)
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Slice<ChatMessageResponse>> getMessages(@PathVariable String roomId,
                                                                  @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(chatService.getChatMessages(roomId, page, 20));
    }
}