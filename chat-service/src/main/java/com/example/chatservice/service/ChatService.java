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
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOTAL_UNREAD_PREFIX = "USER_TOTAL_UNREAD:";

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
        ChatRoomEntity chatRoom = getOrCreateChatRoom(senderId, request.getReceiverId());

        // 1. [DB Insert] 메시지 저장
        ChatMessageEntity message = ChatMessageEntity.builder()
                .roomId(chatRoom.getId())
                .senderId(senderId)
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(message);

        // 2. [DB Update] 채팅방 메타데이터 갱신
        chatRoom.updateLastMessage(message.getContent(), message.getCreatedAt()); // 채팅방의 마지막 메시지 최신화
        chatRoom.updateReadTime(senderId, message.getCreatedAt()); // 채팅방의 마지막 메시지 시간 최신화

        chatRoom.increaseUnreadCount(request.getReceiverId()); // 상대방의 읽지 않은 메시지 수 + 1
        chatRoom.resetUnreadCount(senderId); // 나는 0 (방어 로직)

        chatRoomRepository.save(chatRoom);

        // 3. [Redis Update] 상대방 전체 안 읽은 개수 +1
        String redisKey = TOTAL_UNREAD_PREFIX + request.getReceiverId();
        redisTemplate.opsForValue().increment(redisKey);

        // ** 위는 DB 저장에 관련된 부분 / 아래는 실시간으로 메시지 발행해서 뿌리는 부분 ** //
        
        // 4. Pub/Sub
        ChatMessageResponse response = ChatMessageResponse.builder()
                .type(MessageType.TALK)
                .id(message.getId())
                .roomId(chatRoom.getId())
                .senderId(senderId)
                .receiverId(request.getReceiverId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .readCount(1)
                .build();

        redisPublisher.publish(redisPublisher.getTopic(), response); // redisPublisher.publish(chatroom, response); <- chatroom이라는 토픽(채널)로 메시지 던짐
    }

    // 읽음 처리 로직
    private void markAsRead(Long userId, String roomId) {
        ChatRoomEntity chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        int currentUnreadCount = chatRoom.getUnreadCount(userId);

        if (currentUnreadCount > 0) {
            // 1. [DB Update] 내 카운트 0으로 초기화
            chatRoom.resetUnreadCount(userId);
            chatRoom.updateReadTime(userId, LocalDateTime.now());
            chatRoomRepository.save(chatRoom);

            // 2. [Redis Update] 전체 카운트 차감
            String redisKey = TOTAL_UNREAD_PREFIX + userId;
            redisTemplate.opsForValue().decrement(redisKey, currentUnreadCount);
        }

        // 3. Pub/Sub (상대방에게 읽음 알림)
        ChatMessageResponse response = ChatMessageResponse.builder()
                .type(MessageType.READ)
                .roomId(roomId)
                .senderId(userId)
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
        Slice<ChatRoomEntity> roomSlice = chatRoomRepository.findByUserId(userId, pageable);
        List<ChatRoomEntity> rooms = roomSlice.getContent();

        Set<Long> otherUserIds = rooms.stream()
                .map(room -> room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id())
                .collect(Collectors.toSet());

        Map<Long, ChatUserProfileEntity> profileMap = chatUserProfileRepository.findAllById(otherUserIds)
                .stream()
                .collect(Collectors.toMap(ChatUserProfileEntity::getUserId, Function.identity()));

        List<ChatRoomResponse> roomResponses = rooms.stream().map(room -> {
            Long otherUserId = room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id();
            ChatUserProfileEntity profile = profileMap.get(otherUserId);
            String otherUserName = (profile != null) ? profile.getName() : "알 수 없음";

            // [N+1 해결] DB 쿼리 없이 Entity 필드에서 바로 get
            int unreadCount = room.getUnreadCount(userId);

            return ChatRoomResponse.builder()
                    .roomId(room.getId())
                    .otherUserId(otherUserId)
                    .otherUserName(otherUserName)
                    .lastMessage(room.getLastMessage())
                    .lastMessageTime(room.getLastMessageTime())
                    .unreadCount(unreadCount)
                    .build();
        }).collect(Collectors.toList());

        return new SliceImpl<>(roomResponses, pageable, roomSlice.hasNext());
    }

    public Slice<ChatMessageResponse> getChatMessages(String roomId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .map(ChatMessageResponse::from);
    }

    // 전체 안 읽은 메시지 수 조회 API용 메서드
    public Long getTotalUnreadCount(Long userId) {
        String redisKey = TOTAL_UNREAD_PREFIX + userId;
        Object count = redisTemplate.opsForValue().get(redisKey);
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }

    @Transactional
    public void sendInternalMessage(Long senderId, Long receiverId, String content) {
        // 1. 내부 요청을 ChatMessageRequest DTO로 변환
        ChatMessageRequest request = ChatMessageRequest.builder()
                .receiverId(receiverId)
                .content(content)
                .type(MessageType.TALK) // 일반 대화 메시지로 처리
                .build();

        // 2. 기존 메시지 전송 로직 호출 (DB 저장, Redis 갱신, Pub/Sub 모두 수행됨)
        sendTalkMessage(senderId, request);
    }
}