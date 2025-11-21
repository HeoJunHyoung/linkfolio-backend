package com.example.chatservice.service;

import com.example.chatservice.dto.ChatMessageRequest;
import com.example.chatservice.dto.ChatMessageResponse;
import com.example.chatservice.dto.ChatRoomResponse;
import com.example.chatservice.dto.enumerate.MessageType;
import com.example.chatservice.entity.ChatMessageEntity;
import com.example.chatservice.entity.ChatRoomEntity;
import com.example.chatservice.entity.ChatUserProfileEntity;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.ChatUserProfileRepository;
import com.example.chatservice.service.redis.RedisPublisher;
import com.mongodb.DuplicateKeyException;
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


    @Transactional
    public void handleMessage(Long senderId, ChatMessageRequest request) {
        if (request.getType() == MessageType.READ) {
            // 읽음 표시
            markAsRead(senderId, request.getRoomId());
        } else if (request.getType() == MessageType.TYPING) {
            // 입력 중 신호 전송 (DB 저장 X, 바로 전송)
            sendTypingSignal(senderId, request.getRoomId());
        } else {
            // 일반 전송
            sendTalkMessage(senderId, request);
        }
    }

    // 일반 메시지 전송 로직
    private void sendTalkMessage(Long senderId, ChatMessageRequest request) {
        // 1. 방 조회/생성
        ChatRoomEntity chatRoom = getOrCreateChatRoom(senderId, request.getReceiverId());

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

        // 4. Redis 발행 (Response DTO 생성)
        ChatMessageResponse response = ChatMessageResponse.builder()
                .type(MessageType.TALK) // 타입 지정
                .id(message.getId())
                .roomId(chatRoom.getId())
                .senderId(senderId)
                .receiverId(request.getReceiverId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .readCount(1)
                .build();

        redisPublisher.publish(redisPublisher.getTopic(), response);
    }

    // 읽음 처리 로직
    private void markAsRead(Long userId, String roomId) {
        ChatRoomEntity chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        chatRoom.updateReadTime(userId, LocalDateTime.now());
        chatRoomRepository.save(chatRoom);

        // 읽음 이벤트 발행
        ChatMessageResponse response = ChatMessageResponse.builder()
                .type(MessageType.READ) // 타입 지정
                .roomId(roomId)
                .senderId(userId) // 읽은 사람
                .build();

        redisPublisher.publish(redisPublisher.getTopic(), response);
    }

    // 입력 중 신호 발송 메서드
    private void sendTypingSignal(Long senderId, String roomId) {
        ChatMessageResponse response = ChatMessageResponse.builder()
                .type(MessageType.TYPING)
                .roomId(roomId)
                .senderId(senderId)
                .build();

        redisPublisher.publish(redisPublisher.getTopic(), response);
    }

    private ChatRoomEntity getOrCreateChatRoom(Long user1, Long user2) {
        Long minId = Math.min(user1, user2);
        Long maxId = Math.max(user1, user2);

        // 1차 시도: 조회
        return chatRoomRepository.findByUser1IdAndUser2Id(minId, maxId)
                .orElseGet(() -> {
                    try {
                        // 방이 없으면 생성 시도
                        ChatRoomEntity newRoom = ChatRoomEntity.builder()
                                .user1Id(minId)
                                .user2Id(maxId)
                                .build();
                        return chatRoomRepository.save(newRoom);
                    } catch (DuplicateKeyException e) {
                        // 동시성 이슈로 이미 방이 생성된 경우, 다시 조회하여 반환
                        log.info("ChatRoom concurrent creation detected. Fetching existing room. User1: {}, User2: {}", minId, maxId);
                        return chatRoomRepository.findByUser1IdAndUser2Id(minId, maxId)
                                .orElseThrow(() -> new RuntimeException("ChatRoom creation failed and fetch failed."));
                    }
                });
    }

    /**
     * 내 채팅방 목록 조회
     */
    public Slice<ChatRoomResponse> getMyChatRooms(Long userId, Pageable pageable) {
        // 1. DB에서 Slice로 조회
        Slice<ChatRoomEntity> roomSlice = chatRoomRepository.findByUserId(userId, pageable);
        List<ChatRoomEntity> rooms = roomSlice.getContent();

        // 2. 상대방 ID 수집 (Batch 조회를 위함)
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

            return ChatRoomResponse.builder()
                    .roomId(room.getId())
                    .otherUserId(otherUserId)
                    .otherUserName(otherUserName)
                    .lastMessage(room.getLastMessage())
                    .lastMessageTime(room.getLastMessageTime())
                    .unreadCount((int) unreadCount)
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