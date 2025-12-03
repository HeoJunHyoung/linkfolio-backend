package com.example.chatservice.controller;

import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ChatRoomResponse;
import com.example.chatservice.service.ChatService;
import com.example.commonmodule.dto.security.AuthUser;
import com.example.commonmodule.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat API", description = "채팅방 및 메시지 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "내 채팅방 목록 조회", description = "로그인한 사용자가 참여 중인 채팅방 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = Slice.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/rooms")
    public ResponseEntity<Slice<ChatRoomResponse>> getMyRooms(@AuthenticationPrincipal AuthUser authUser,
                                                              @PageableDefault(size = 5, sort = "lastMessageTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chatService.getMyChatRooms(authUser.getUserId(), pageable));
    }

    @Operation(summary = "채팅방 메시지 내용 조회", description = "특정 채팅방의 메시지 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = Slice.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Slice<ChatMessageResponse>> getMessages(@PathVariable String roomId,
                                                                  @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(chatService.getChatMessages(roomId, page, 20));
    }

    @Operation(summary = "전체 안 읽은 메시지 수 조회", description = "메인 페이지 뱃지용으로 사용자의 안 읽은 메시지 총 개수를 반환합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getTotalUnreadCount(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(chatService.getTotalUnreadCount(authUser.getUserId()));
    }
}