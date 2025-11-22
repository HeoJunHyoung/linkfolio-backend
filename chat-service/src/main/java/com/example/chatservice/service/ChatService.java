package com.example.chatservice.service;

import com.example.chatservice.dto.*;
import com.example.chatservice.dto.enumerate.MessageType;
import com.example.chatservice.entity.*;
import com.example.chatservice.repository.*;
import com.example.chatservice.service.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatUserProfileRepository chatUserProfileRepository;
    private final RedisPublisher redisPublisher;

    // Atomic Update를 위한 템플릿
    private final MongoTemplate mongoTemplate;

    @Transactional
    public void handleMessage(Long senderId, ChatMessageRequest request) {
        if (request.getType() == MessageType.READ) {
            markAsRead(senderId, request.getRoomId());
        } else if (request.getType() == MessageType.TYPING) {
            redisPublisher.publish(redisPublisher.getTopic(), ChatMessageResponse.builder()
                    .type(MessageType.TYPING).roomId(request.getRoomId()).senderId(senderId).build());
        } else {
            sendTalkMessage(senderId, request);
        }
    }

    /**
     * 메시지 전송 (MongoDB Atomic Update 적용)
     */
    private void sendTalkMessage(Long senderId, ChatMessageRequest request) {
        // 1. 방 조회/생성
        ChatRoomEntity chatRoom = getOrCreateChatRoom(senderId, request.getReceiverId());
        String roomId = chatRoom.getId();

        // 2. [내 상태] 확인 (나갔으면 재입장)
        ChatRoomUserEntity me = getOrCreateChatUser(roomId, senderId);
        if (!me.isActive()) {
            me.rejoin();
            chatRoomUserRepository.save(me);
        }

        // 3. [상대방 상태] 확인 및 "자동 초대"
        // 상대방 데이터를 가져와서, 나간 상태(false)라면 다시 재입장(true)
        ChatRoomUserEntity receiver = getOrCreateChatUser(roomId, request.getReceiverId());
        if (!receiver.isActive()) {
            receiver.rejoin(); // isActive = true, visibleFrom = now()
            chatRoomUserRepository.save(receiver);
        }

        // 4. 메시지 저장
        // (중요: rejoin()을 먼저 호출했으므로 visibleFrom 시간이 메시지 시간보다 조금 앞서게 되어, 이 메시지는 볼 수 있음)
        ChatMessageEntity message = ChatMessageEntity.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(message);

        // 5. 방 메타데이터 갱신
        chatRoom.updateLastMessage(message.getContent(), message.getCreatedAt());
        chatRoomRepository.save(chatRoom);

        // 6. [Atomic Update] 상대방들의 unreadCount +1
        // 이제 상대방이 isActive=true가 되었으므로, 이 쿼리에서 정상적으로 카운트가 증가
        Query query = new Query(Criteria.where("roomId").is(roomId)
                .and("userId").ne(senderId)
                .and("isActive").is(true));

        Update update = new Update().inc("unreadCount", 1);
        mongoTemplate.updateMulti(query, update, ChatRoomUserEntity.class);

        // 7. Redis 발행
        redisPublisher.publish(redisPublisher.getTopic(), ChatMessageResponse.from(message));
    }

    /**
     * 읽음 처리 (DB 업데이트)
     */
    private void markAsRead(Long userId, String roomId) {
        ChatRoomUserEntity me = chatRoomUserRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 안 읽은 수 0으로 초기화
        me.resetUnreadCount();
        chatRoomUserRepository.save(me);

        // (옵션) 상대방에게 '읽음' 상태 전파 필요 시 Pub/Sub 추가
    }

    /**
     * 채팅방 나가기
     */
    @Transactional
    public void leaveChatRoom(Long userId, String roomId) {
        ChatRoomUserEntity me = chatRoomUserRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        me.leave(); // isActive = false
        chatRoomUserRepository.save(me);
    }

    /**
     * 채팅방 목록 조회
     */
    public Slice<ChatRoomResponse> getMyChatRooms(Long userId, Pageable pageable) {
        // 1. 내 활성 채팅방 목록 조회 (ChatUser)
        Slice<ChatRoomUserEntity> myUserEntities = chatRoomUserRepository.findByUserIdAndIsActiveTrue(userId, pageable);

        List<String> roomIds = myUserEntities.stream().map(ChatRoomUserEntity::getRoomId).toList();

        // 2. 채팅방 상세 정보 조회 (ChatRoom) - Batch Fetch
        Map<String, ChatRoomEntity> roomMap = chatRoomRepository.findAllById(roomIds).stream()
                .collect(Collectors.toMap(ChatRoomEntity::getId, Function.identity()));

        // 3. 상대방 ID 수집 (1:1 가정)
        Set<Long> otherUserIds = new HashSet<>();
        roomMap.values().forEach(room -> {
            if (room.getUser1Id() != null && room.getUser2Id() != null) {
                otherUserIds.add(room.getUser1Id().equals(userId) ? room.getUser2Id() : room.getUser1Id());
            }
        });

        // 4. 프로필 캐시 조회 (ChatUserProfile)
        Map<Long, ChatUserProfileEntity> profileMap = chatUserProfileRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(ChatUserProfileEntity::getUserId, Function.identity()));

        // 5. 조합 및 변환
        List<ChatRoomResponse> responses = myUserEntities.getContent().stream().map(me -> {
            ChatRoomEntity room = roomMap.get(me.getRoomId());
            Long otherId = (room.getUser1Id().equals(userId)) ? room.getUser2Id() : room.getUser1Id();
            ChatUserProfileEntity otherProfile = profileMap.get(otherId);

            return ChatRoomResponse.builder()
                    .roomId(room.getId())
                    .otherUserId(otherId)
                    .otherUserName(otherProfile != null ? otherProfile.getName() : "(알 수 없음)")
                    .lastMessage(room.getLastMessage())
                    .lastMessageTime(room.getLastMessageTime())
                    .unreadCount(me.getUnreadCount()) // 내 Entity에 저장된 값 사용
                    .build();
        }).collect(Collectors.toList());

        return new SliceImpl<>(responses, pageable, myUserEntities.hasNext());
    }

    /**
     * 메시지 내역 조회 (시간 필터링)
     */
    public Slice<ChatMessageResponse> getChatMessages(String roomId, Long userId, int page, int size) {
        ChatRoomUserEntity me = chatRoomUserRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        PageRequest pageable = PageRequest.of(page, size);

        // 내가 볼 수 있는 시간(visibleFrom) 이후의 메시지만 가져옴
        return chatMessageRepository.findAllByRoomIdAndCreatedAtAfter(roomId, me.getVisibleFrom(), pageable)
                .map(ChatMessageResponse::from);
    }

    // 전체 안 읽은 메시지 수 (Aggregation)
    public Long getTotalUnreadCount(Long userId) {
        // MongoDB Aggregation을 써서 sum(unreadCount)를 구하거나, 성능을 위해 별도 필드로 관리 가능.
        // 여기서는 간단히 목록 조회 후 합산 예시 (실무에선 Aggregation 추천)
        List<ChatRoomUserEntity> allMyRooms = chatRoomUserRepository.findByUserIdAndIsActiveTrue(userId, Pageable.unpaged()).getContent();
        return allMyRooms.stream().mapToLong(ChatRoomUserEntity::getUnreadCount).sum();
    }

    // --- Helper Methods ---
    private ChatRoomEntity getOrCreateChatRoom(Long user1, Long user2) {
        Long minId = Math.min(user1, user2);
        Long maxId = Math.max(user1, user2);
        return chatRoomRepository.findByUser1IdAndUser2Id(minId, maxId)
                .orElseGet(() -> chatRoomRepository.save(new ChatRoomEntity(minId, maxId)));
    }

    private ChatRoomUserEntity getOrCreateChatUser(String roomId, Long userId) {
        return chatRoomUserRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(() -> chatRoomUserRepository.save(new ChatRoomUserEntity(roomId, userId)));
    }
}