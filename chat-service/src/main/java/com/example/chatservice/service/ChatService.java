package com.example.chatservice.service;

import com.example.chatservice.dto.ChatMessageRequest;
import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ChatRoomResponse;
import com.example.chatservice.entity.ChatMessageEntity;
import com.example.chatservice.entity.ChatRoomEntity;
import com.example.chatservice.entity.ChatUserProfileEntity;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.ChatUserProfileRepository;
import com.example.chatservice.service.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatUserProfileRepository chatUserProfileRepository;
    private final RedisPublisher redisPublisher;
    private final OnlineStatusService onlineStatusService;

    @Transactional
    public void sendMessage(Long senderId, ChatMessageRequest request) {
        Long receiverId = request.getReceiverId();

        // 1. 방 조회 혹은 생성 (Lazy)
        ChatRoomEntity chatRoom = getOrCreateChatRoom(senderId, receiverId);

        // 2. 메시지 저장
        ChatMessageEntity message = ChatMessageEntity.builder()
                .roomId(chatRoom.getId())
                .senderId(senderId)
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(message);

        // 3. 방 메타데이터 갱신
        chatRoom.updateLastMessage(message.getContent(), message.getCreatedAt());
        chatRoom.updateReadTime(senderId, message.getCreatedAt());
        chatRoomRepository.save(chatRoom);

        // 4. Pub/Sub 전송
        ChatMessageResponse response = ChatMessageResponse.from(message);
        redisPublisher.publish(redisPublisher.getTopic(), response);
    }

    private ChatRoomEntity getOrCreateChatRoom(Long user1, Long user2) {
        Long minId = Math.min(user1, user2);
        Long maxId = Math.max(user1, user2);

        return chatRoomRepository.findByUser1IdAndUser2Id(minId, maxId)
                .orElseGet(() -> {
                    ChatRoomEntity newRoom = ChatRoomEntity.builder()
                            .user1Id(minId)
                            .user2Id(maxId)
                            .build();
                    return chatRoomRepository.save(newRoom);
                });
    }

    /**
     * 내 채팅방 목록 조회
     */
    public Slice<ChatRoomResponse> getMyChatRooms(Long userId, Pageable pageable) {
        // 1. DB에서 Slice로 조회
        Slice<ChatRoomEntity> roomSlice = chatRoomRepository.findByUserId(userId, pageable);
        List<ChatRoomEntity> rooms = roomSlice.getContent();

        // 2. 상대방 ID 수집 (Batch 조회를 위함) - 기존 로직과 동일
        Set<Long> otherUserIds = rooms.stream()
                .map(room -> room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id())
                .collect(Collectors.toSet());

        // 3. 로컬 캐시 DB에서 상대방 정보 일괄 조회
        Map<Long, ChatUserProfileEntity> profileMap = chatUserProfileRepository.findAllById(otherUserIds)
                .stream()
                .collect(Collectors.toMap(ChatUserProfileEntity::getUserId, Function.identity()));

        // 4. DTO 변환
        List<ChatRoomResponse> roomResponses = rooms.stream().map(room -> {
            Long otherUserId = room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id();

            ChatUserProfileEntity profile = profileMap.get(otherUserId);
            String otherUserName = (profile != null) ? profile.getName() : "알 수 없음";

            LocalDateTime myLastRead = room.getLastReadAt().getOrDefault(String.valueOf(userId), LocalDateTime.MIN);
            long unreadCount = chatMessageRepository.countUnreadMessages(room.getId(), myLastRead, userId);
            boolean isOnline = onlineStatusService.isUserOnline(otherUserId);

            return ChatRoomResponse.builder()
                    .roomId(room.getId())
                    .otherUserId(otherUserId)
                    .otherUserName(otherUserName)
                    .lastMessage(room.getLastMessage())
                    .lastMessageTime(room.getLastMessageTime())
                    .unreadCount((int) unreadCount)
                    .isOnline(isOnline)
                    .build();
        }).collect(Collectors.toList());

        // 5. SliceImpl로 감싸서 반환 (hasNext 정보 유지)
        return new SliceImpl<>(roomResponses, pageable, roomSlice.hasNext());
    }

    public Slice<ChatMessageResponse> getChatMessages(String roomId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .map(ChatMessageResponse::from);
    }
}