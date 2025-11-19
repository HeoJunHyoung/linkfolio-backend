package com.example.chatservice.service;

import com.example.chatservice.config.RedisConfig;
import com.example.chatservice.dto.ChatMessageDto;
import com.example.chatservice.dto.InternalUserProfileResponse;
import com.example.chatservice.entity.ChatMessageEntity;
import com.example.chatservice.entity.ChatRoomEntity;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.handler.ChatWebSocketHandler;
import com.example.chatservice.client.UserServiceClient; // FeignClient 사용
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ChatWebSocketHandler webSocketHandler;
    private final UserServiceClient userServiceClient; // FeignClient

    // Redis Key Prefix
    private static final String UNREAD_COUNT_KEY = "chat:unread:room:%s:user:%d";
    private static final String ONLINE_STATUS_KEY = "chat:online:%s"; // Key: chat:online:roomId
    private static final String TYPING_STATUS_KEY = "chat:typing:room:%s:user:%d";

    /**
     * 1. 메시지 전송 및 저장 (Lazy Room Creation 포함)
     */
    public Mono<Void> handleSendMessage(ChatMessageDto messageDto) {
        Long senderId = messageDto.getSenderId();
        String roomId = messageDto.getRoomId();

        // 1. 채팅방이 존재하는지 확인 (roomId가 없을 경우 생성 로직 포함)
        Mono<ChatRoomEntity> roomMono = roomRepository.findById(roomId)
                .switchIfEmpty(Mono.defer(() -> createRoomLazily(roomId, senderId))); // Lazy Creation

        return roomMono.flatMap(room -> {
            // 2. 메시지 엔티티 생성 및 저장
            ChatMessageEntity messageEntity = ChatMessageEntity.of(
                    room.getId(), senderId, messageDto.getContent());

            return messageRepository.save(messageEntity)
                    .doOnNext(savedMessage -> log.info("메시지 저장 완료. MsgId: {}", savedMessage.getId()))
                    .flatMap(savedMessage -> {

                        // 3. ChatRoom 메타데이터 업데이트
                        room.updateLastMessage(senderId, savedMessage.getContent());

                        // 4. 상대방의 안 읽은 메시지 카운트 증가
                        Long receiverId = room.getOtherUserId(senderId);
                        Mono<Long> unreadCountMono = increaseUnreadCount(room.getId(), receiverId);

                        // 5. Redis Pub/Sub 발행 (실시간 메시지 전파)
                        messageDto.setCreatedAt(savedMessage.getCreatedAt());
                        Mono<Long> pubMono = redisTemplate.convertAndSend(
                                RedisConfig.CHAT_TOPIC_NAME, messageDto
                        );

                        // 6. 채팅방 목록 갱신 이벤트 발행 (메타데이터 변경 알림)
                        Mono<Long> roomUpdateMono = redisTemplate.convertAndSend(
                                RedisConfig.GLOBAL_NOTIFY_TOPIC_NAME, room
                        );

                        return Mono.when(roomRepository.save(room), pubMono, roomUpdateMono, unreadCountMono);
                    });
        }).then();
    }

    /**
     * Lazy Room Creation 로직
     */
    private Mono<ChatRoomEntity> createRoomLazily(String tempRoomId, Long senderId) {
        log.info("새로운 채팅방 Lazy 생성 시작. Temp RoomId: {}", tempRoomId);

        String[] ids = tempRoomId.split("_");
        if (ids.length != 2) {
            return Mono.error(new IllegalArgumentException("Invalid temporary room ID format."));
        }

        Long userId1 = Long.valueOf(ids[0]);
        Long userId2 = Long.valueOf(ids[1]);

        // 1. ID 정렬: ChatRoomEntity의 CompoundIndex와 일관성을 위해 ID를 정렬합니다.
        Long sortedUser1Id = userId1.compareTo(userId2) < 0 ? userId1 : userId2;
        Long sortedUser2Id = userId1.compareTo(userId2) < 0 ? userId2 : userId1;

        // 2. 혹시 모를 중복 방 체크 (ID 정렬 후 조회)
        return roomRepository.findByUser1IdAndUser2Id(sortedUser1Id, sortedUser2Id)
                .next() // Flux -> Mono로 변환 (첫 번째 요소를 가져오거나, 없으면 empty)
                .switchIfEmpty(Mono.defer(() -> { // 방이 없는 경우에만 실행

                    // 3. 참여자 프로필 정보 조회 (Blocking Feign 호출을 Mono.fromCallable로 감싸 논블로킹 환경에 통합)
                    Mono<InternalUserProfileResponse> user1ProfileMono = Mono.fromCallable(() ->
                            userServiceClient.getInternalUserProfile(sortedUser1Id)
                    ).subscribeOn(Schedulers.boundedElastic()); // Blocking 호출을 위한 스케줄러 지정

                    Mono<InternalUserProfileResponse> user2ProfileMono = Mono.fromCallable(() ->
                            userServiceClient.getInternalUserProfile(sortedUser2Id)
                    ).subscribeOn(Schedulers.boundedElastic());

                    return Mono.zip(user1ProfileMono, user2ProfileMono)
                            .flatMap(tuple -> {
                                InternalUserProfileResponse user1 = tuple.getT1();
                                InternalUserProfileResponse user2 = tuple.getT2();

                                ChatRoomEntity newRoom = ChatRoomEntity.of(
                                        user1.getUserId(), user2.getUserId(),
                                        user1.getEmail(), user2.getEmail());

                                return roomRepository.save(newRoom)
                                        .doOnSuccess(r -> log.info("ChatRoom MongoDB 저장 완료. RoomId: {}", r.getId()));
                            });
                }))
                .onErrorResume(e -> {
                    log.error("채팅방 생성 중 오류 발생: {}", e.getMessage());
                    return Mono.error(new RuntimeException("채팅방 생성에 실패했습니다."));
                });
    }

    /**
     * 2. 메시지 읽음 처리 (읽지 않은 메시지 카운트 초기화 및 Read Receipt)
     */
    public Mono<Void> handleReadMessage(String roomId, Long readerId) {

        return roomRepository.findById(roomId)
                .flatMap(room -> {
                    LocalDateTime lastReadAt = LocalDateTime.now();

                    // 1. ChatRoom의 lastReadAt 갱신
                    room.updateLastReadAt(readerId, lastReadAt);
                    Mono<ChatRoomEntity> updateRoomMono = roomRepository.save(room);

                    // 2. Redis 안 읽은 메시지 카운트 초기화 (0으로 설정)
                    Mono<Boolean> resetCountMono = resetUnreadCount(roomId, readerId);

                    // 3. 메시지 엔티티의 readAt 갱신 (Optional: 모든 메시지에 대해 readAt 업데이트)
                    // MongoDB의 updateMany 기능을 사용하여 성능 최적화 필요
                    // 이 로직은 read receipt(읽음 확인) 기능을 위해 사용됨
                    Mono<Long> updateMessagesMono = messageRepository.markMessagesAsRead(roomId, room.getOtherUserId(readerId), room.getMyLastReadAt(readerId))
                            .doOnNext(count -> log.info("{}개의 메시지 읽음 처리 완료.", count));

                    // 4. 상대방에게 읽음 확인 이벤트 PUBLISH (READ 타입)
                    ChatMessageDto readReceipt = ChatMessageDto.builder()
                            .type(ChatMessageDto.MessageType.READ)
                            .roomId(roomId)
                            .senderId(readerId)
                            .build();

                    Mono<Long> pubMono = redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC_NAME, readReceipt);

                    return Mono.when(updateRoomMono, resetCountMono, updateMessagesMono, pubMono);
                }).then();
    }

    /**
     * 3. 읽지 않은 메시지 카운트 조회
     */
    public Mono<Long> getUnreadCount(String roomId, Long userId) {
        String key = String.format(UNREAD_COUNT_KEY, roomId, userId);
        return redisTemplate.opsForValue().get(key)
                .map(obj -> {
                    try {
                        return Long.valueOf(obj.toString());
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .defaultIfEmpty(0L);
    }

    /**
     * 4. 읽지 않은 메시지 카운트 증가 (메시지 수신 시)
     */
    public Mono<Long> increaseUnreadCount(String roomId, Long receiverId) {
        String key = String.format(UNREAD_COUNT_KEY, roomId, receiverId);
        return redisTemplate.opsForValue().increment(key)
                .doOnSuccess(count -> log.debug("Unread count increased. RoomId: {}, UserId: {}, Count: {}", roomId, receiverId, count));
    }

    /**
     * 5. 읽지 않은 메시지 카운트 초기화 (읽음 처리 시)
     */
    public Mono<Boolean> resetUnreadCount(String roomId, Long userId) {
        String key = String.format(UNREAD_COUNT_KEY, roomId, userId);
        return redisTemplate.opsForValue().set(key, 0L);
    }

    /**
     * 6. 실시간 입력 중 상태 발행
     */
    public Mono<Void> publishTypingStatus(String roomId, Long userId, String isTyping) {
        // Pub/Sub을 통해 TYPING 상태 전파 (Redis Pub/Sub)
        ChatMessageDto typingStatus = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.TYPING)
                .roomId(roomId)
                .senderId(userId)
                .content(isTyping) // "true" or "false"
                .build();

        return redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC_NAME, typingStatus)
                .then();
    }

    /**
     * 7. 사용자 온라인/오프라인 상태 발행 (WebSocket 연결/해제 시)
     */
    public Mono<Void> publishUserOnlineStatus(String roomId, Long userId, boolean isOnline) {
        // 온라인/오프라인 상태를 Redis String 또는 Set에 저장 (선택 사항) 후, Pub/Sub 발행

        ChatMessageDto statusMessage = ChatMessageDto.builder()
                .type(isOnline ? ChatMessageDto.MessageType.ENTER : ChatMessageDto.MessageType.EXIT)
                .roomId(roomId)
                .senderId(userId)
                .content(String.valueOf(isOnline))
                .build();

        return redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC_NAME, statusMessage)
                .then();
    }

    /**
     * 8. 나의 모든 채팅방 목록 조회 (Redis Pub/Sub으로 실시간 업데이트)
     */
    public Flux<ChatRoomEntity> getMyChatRooms(Long userId) {
        // ChatRoomRepository의 findMyRooms 쿼리를 사용
        return roomRepository.findMyRooms(userId);
    }
}