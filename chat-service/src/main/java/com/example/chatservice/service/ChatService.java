package com.example.chatservice.service;

import com.example.chatservice.client.InternalUserProfileResponse;
import com.example.chatservice.client.UserClient;
import com.example.chatservice.dto.ChatMessageRequest;
import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ChatRoomResponse;
import com.example.chatservice.entity.ChatMessageEntity;
import com.example.chatservice.entity.ChatRoomEntity;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.service.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RedisPublisher redisPublisher;
    private final UserClient userClient;
    private final OnlineStatusService onlineStatusService;

    /**
     * 메시지 전송 (Lazy Room Creation)
     */
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
    public List<ChatRoomResponse> getMyChatRooms(Long userId) {
        List<ChatRoomEntity> rooms = chatRoomRepository.findAllByUserId(userId);

        return rooms.stream().map(room -> {
            Long otherUserId = room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id();
            LocalDateTime myLastRead = room.getLastReadAt().getOrDefault(String.valueOf(userId), LocalDateTime.MIN);

            long unreadCount = chatMessageRepository.countUnreadMessages(room.getId(), myLastRead, userId);
            boolean isOnline = onlineStatusService.isUserOnline(otherUserId);

            String otherUserName = "Unknown";
            try {
                InternalUserProfileResponse profile = userClient.getInternalUserProfile(otherUserId);
                otherUserName = profile.getName();
            } catch (Exception e) {
                log.error("Failed to fetch user profile", e);
            }

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
    }

    public Slice<ChatMessageResponse> getChatMessages(String roomId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .map(ChatMessageResponse::from);
    }
}