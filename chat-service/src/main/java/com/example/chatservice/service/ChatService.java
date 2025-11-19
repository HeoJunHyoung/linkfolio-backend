// chat-service/src/main/java/com/example/chatservice/service/ChatService.java
package com.example.chatservice.service;

import com.example.chatservice.client.UserServiceClient;
import com.example.chatservice.config.RedisConfig;
import com.example.chatservice.dto.ChatMessageDto;
import com.example.chatservice.dto.InternalUserProfileResponse;
import com.example.chatservice.dto.response.ChatMessageResponse;
import com.example.chatservice.dto.response.ChatRoomDetailResponse;
import com.example.chatservice.entity.ChatMessageEntity;
import com.example.chatservice.entity.ChatRoomEntity;
import com.example.chatservice.exception.ChatErrorCode;
import com.example.chatservice.handler.ChatWebSocketHandler;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.commonmodule.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ChatWebSocketHandler webSocketHandler;
    private final UserServiceClient userServiceClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("YYYY년 MM월 DD일");

    // Redis Key Prefix
    private static final String UNREAD_COUNT_KEY = "chat:unread:room:%s:user:%d";      // 개별 배지
    private static final String UNREAD_COUNT_SUM_KEY = "chat:unread:sum:%d";           // 총합 배지
    private static final String USER_CURRENT_ROOM_KEY = "chat:user:room:%d";           // 사용자의 현재 접속 중인 방 ID
    // private static final String ONLINE_STATUS_KEY = "chat:online:%d"; // (Pub/Sub으로 대체)

    /**
     * 1. 채팅방 조회 또는 생성 (Lazy Creation 시작점)
     * Controller의 GET /partner/{partnerId}에서 호출됨.
     */
    public Mono<ChatRoomEntity> getOrCreateChatRoom(Long myUserId, Long partnerId) {

        if (myUserId.equals(partnerId)) {
            return Mono.error(new BusinessException(ChatErrorCode.INVALID_ROOM_PARTNER));
        }

        // 1. ID 정렬 (Compound Index에 맞추기 위해)
        Long user1Id = myUserId.compareTo(partnerId) < 0 ? myUserId : partnerId;
        Long user2Id = myUserId.compareTo(partnerId) < 0 ? partnerId : myUserId;

        // 2. 채팅방 조회 시도
        return roomRepository.findByUser1IdAndUser2Id(user1Id, user2Id)
                .next()
                .switchIfEmpty(Mono.defer(() -> createRoom(user1Id, user2Id)))
                .doOnSuccess(room -> log.info("채팅방 조회 또는 생성 완료. RoomId: {}", room.getId()));
    }

    /**
     * 실제 채팅방 생성 로직 (Blocking Feign 호출 포함)
     */
    private Mono<ChatRoomEntity> createRoom(Long user1Id, Long user2Id) {
        log.info("새로운 채팅방 생성 시작. Users: {} vs {}", user1Id, user2Id);

        // Feign Client 호출은 Blocking이므로 Schedulers.boundedElastic 사용
        Mono<InternalUserProfileResponse> user1ProfileMono = Mono.fromCallable(() ->
                userServiceClient.getInternalUserProfile(user1Id)
        ).subscribeOn(Schedulers.boundedElastic());

        Mono<InternalUserProfileResponse> user2ProfileMono = Mono.fromCallable(() ->
                userServiceClient.getInternalUserProfile(user2Id)
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
                })
                .onErrorResume(e -> {
                    log.error("채팅방 생성 중 오류 발생", e);
                    return Mono.error(new BusinessException(ChatErrorCode.CHAT_ROOM_CREATION_FAILED));
                });
    }

    /**
     * 2. 메시지 전송 및 저장 (WebSocket 내부에서 사용)
     */
    public Mono<Void> handleSendMessage(ChatMessageDto messageDto) {
        Long senderId = messageDto.getSenderId();
        String roomId = messageDto.getRoomId();

        return roomRepository.findById(roomId)
                .switchIfEmpty(Mono.error(new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)))
                .flatMap(room -> {
                    ChatMessageEntity messageEntity = ChatMessageEntity.of(
                            room.getId(), senderId, messageDto.getContent());

                    return messageRepository.save(messageEntity)
                            .flatMap(savedMessage -> {

                                room.updateLastMessage(senderId, savedMessage.getContent());

                                // 상대방에게만 안 읽은 메시지 카운트 증가
                                Long receiverId = room.getOtherUserId(senderId);
                                // 총합 배지 증가 포함
                                Mono<Long> unreadCountMono = increaseUnreadCount(room.getId(), receiverId);

                                // 메시지 DTO에 생성 시간 추가 후 Pub/Sub 발행
                                messageDto.setCreatedAt(savedMessage.getCreatedAt());
                                Mono<Long> pubMono = redisTemplate.convertAndSend(
                                        RedisConfig.CHAT_TOPIC_NAME, messageDto
                                );

                                // 채팅방 목록 갱신 이벤트 발행 (메타데이터 변경 알림)
                                Mono<Long> roomUpdateMono = redisTemplate.convertAndSend(
                                        RedisConfig.GLOBAL_NOTIFY_TOPIC_NAME, room
                                );

                                return Mono.when(roomRepository.save(room), pubMono, roomUpdateMono, unreadCountMono);
                            });
                }).then();
    }

    /**
     * 3. 메시지 읽음 처리 (WebSocket 연결 또는 READ 이벤트 수신 시)
     */
    public Mono<Void> handleReadMessage(String roomId, Long readerId) {

        return roomRepository.findById(roomId)
                .switchIfEmpty(Mono.error(new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)))
                .flatMap(room -> {
                    LocalDateTime lastReadAt = LocalDateTime.now();

                    // 1. ChatRoom의 lastReadAt 갱신
                    room.updateLastReadAt(readerId, lastReadAt);
                    Mono<ChatRoomEntity> updateRoomMono = roomRepository.save(room);

                    // 2. Redis 안 읽은 메시지 카운트 초기화 (총합 배지 감소 포함)
                    Mono<Boolean> resetCountMono = resetUnreadCount(roomId, readerId);

                    // 3. 메시지 엔티티의 readAt 갱신 (Read Receipt 활성화)
                    // 상대방이 보낸 메시지 중, 내가 마지막으로 읽은 시점(업데이트 직전 시점) 이후 메시지를 읽음 처리
                    Mono<Long> updateMessagesMono = messageRepository.markMessagesAsRead(
                            roomId,
                            room.getOtherUserId(readerId),
                            room.getMyLastReadAt(readerId)
                    ).doOnNext(count -> log.info("{}개의 메시지 읽음 처리 완료.", count));

                    // 4. 상대방에게 읽음 확인 이벤트 PUBLISH (READ 타입)
                    ChatMessageDto readReceipt = ChatMessageDto.builder()
                            .type(ChatMessageDto.MessageType.READ)
                            .roomId(roomId)
                            .senderId(readerId)
                            // 읽음 처리된 메시지 개수를 content에 담아 전송
                            .content(String.valueOf(updateMessagesMono.blockOptional().orElse(0L)))
                            .build();

                    Mono<Long> pubMono = redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC_NAME, readReceipt);

                    return Mono.when(updateRoomMono, resetCountMono, updateMessagesMono, pubMono);
                }).then();
    }

    /**
     * 4. 채팅방 상세 정보 및 메시지 기록 조회 (날짜 구분선 로직 포함)
     */
    public Mono<ChatRoomDetailResponse> getChatRoomDetailWithMessages(String roomId, Long myUserId) {

        Mono<ChatRoomEntity> roomMono = roomRepository.findById(roomId)
                .switchIfEmpty(Mono.error(new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)));

        // 메시지 기록 조회 (최신순)
        Flux<ChatMessageEntity> messagesFlux = messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId);

        // 메시지 응답 DTO로 변환 및 날짜 구분선 삽입
        Mono<List<ChatMessageResponse>> messagesResponseMono = messagesFlux
                .collectList()
                .map(messages -> {
                    // 메시지를 시간순(오래된 것부터)으로 정렬
                    List<ChatMessageEntity> sortedMessages = messages.stream()
                            .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt))
                            .collect(Collectors.toList());

                    // 날짜 구분선 삽입 로직
                    List<ChatMessageResponse> finalMessages = new java.util.ArrayList<>();
                    LocalDateTime lastDate = null;

                    for (ChatMessageEntity msg : sortedMessages) {
                        LocalDateTime msgDate = msg.getCreatedAt().toLocalDate().atStartOfDay();

                        // 날짜 변경선 (자정 기준)
                        if (lastDate == null || msgDate.isAfter(lastDate)) {
                            finalMessages.add(ChatMessageResponse.createDateDivider(msgDate.format(DATE_FORMATTER)));
                            lastDate = msgDate;
                        }
                        finalMessages.add(ChatMessageResponse.fromEntity(msg));
                    }

                    // 최종적으로 최신 메시지가 아래로 가도록 순서를 뒤집음
                    Collections.reverse(finalMessages);
                    return finalMessages;
                });

        return Mono.zip(roomMono, messagesResponseMono)
                .map(tuple -> ChatRoomDetailResponse.from(tuple.getT1(), myUserId, tuple.getT2()));
    }

    // --- Redis Pub/Sub 및 목록 조회 로직 ---

    /**
     * 5. 나의 모든 채팅방 목록 조회 (최신 메시지 시간순)
     */
    public Flux<ChatRoomEntity> getMyChatRooms(Long userId) {
        return roomRepository.findMyRooms(userId);
    }

    /**
     * 6. 개별 방 안 읽은 메시지 수 조회 (개별 배지)
     */
    public Mono<Long> getUnreadCount(String roomId, Long userId) {
        String key = String.format(UNREAD_COUNT_KEY, roomId, userId);
        return redisTemplate.opsForValue().get(key)
                .map(obj -> Long.valueOf(obj.toString()))
                .defaultIfEmpty(0L);
    }

    /**
     * 7. 개별 방 안 읽은 메시지 카운트 증가 (총합 배지 업데이트 포함)
     */
    public Mono<Long> increaseUnreadCount(String roomId, Long receiverId) {
        String key = String.format(UNREAD_COUNT_KEY, roomId, receiverId);
        Mono<Long> unreadCountMono = redisTemplate.opsForValue().increment(key);

        // 총합 배지 증가 (1 증가)
        Mono<Long> unreadSumMono = incrementUnreadSum(receiverId, 1);

        return Mono.when(unreadCountMono, unreadSumMono).thenReturn(1L);
    }

    /**
     * 8. 개별 방 안 읽은 메시지 카운트 초기화 (총합 배지 업데이트 포함)
     */
    public Mono<Boolean> resetUnreadCount(String roomId, Long userId) {
        String roomKey = String.format(UNREAD_COUNT_KEY, roomId, userId);

        // 기존 개수를 조회하여 총합에서 뺄 delta 값을 구함
        return getUnreadCount(roomId, userId)
                .flatMap(oldUnreadCount -> {
                    Mono<Boolean> resetRoomCountMono = redisTemplate.opsForValue().set(roomKey, 0L);
                    // 총합 배지 감소
                    Mono<Long> decrementSumMono = incrementUnreadSum(userId, -oldUnreadCount.intValue());

                    return Mono.when(resetRoomCountMono, decrementSumMono).thenReturn(true);
                });
    }

    /**
     * 9. 총 안 읽은 메시지 수 조회 (총합 배지)
     */
    public Mono<Long> getUnreadCountSum(Long userId) {
        String key = String.format(UNREAD_COUNT_SUM_KEY, userId);
        return redisTemplate.opsForValue().get(key)
                .map(obj -> Long.valueOf(obj.toString()))
                .defaultIfEmpty(0L);
    }

    /**
     * 10. Redis에 총합 배지 증가/감소 (내부용)
     */
    public Mono<Long> incrementUnreadSum(Long userId, int delta) {
        String key = String.format(UNREAD_COUNT_SUM_KEY, userId);
        // Redis는 Long 타입으로 저장하므로, 캐스팅이 안전합니다.
        // 음수 처리를 위해 max(0, current + delta) 로직을 추가하여 0 미만이 되지 않도록 방지
        return redisTemplate.opsForValue().get(key)
                .defaultIfEmpty(0L)
                .flatMap(currentValue -> {
                    long current = Long.valueOf(currentValue.toString());
                    long newValue = Math.max(0, current + delta);

                    if (newValue == current) {
                        return Mono.just(current);
                    }

                    return redisTemplate.opsForValue().set(key, newValue).thenReturn(newValue);
                });
    }

    // --- WebSocket 관련 부가 기능 ---

    public Mono<Boolean> saveUserCurrentRoom(Long userId, String roomId) {
        String key = String.format(USER_CURRENT_ROOM_KEY, userId);
        return redisTemplate.opsForValue().set(key, roomId);
    }

    public Mono<Boolean> removeUserCurrentRoom(Long userId) {
        String key = String.format(USER_CURRENT_ROOM_KEY, userId);
        return redisTemplate.delete(key).map(count -> count > 0);
    }

    public Mono<Void> publishUserOnlineStatus(String roomId, Long userId, boolean isOnline) {
        ChatMessageDto statusMessage = ChatMessageDto.builder()
                .type(isOnline ? ChatMessageDto.MessageType.ENTER : ChatMessageDto.MessageType.EXIT)
                .roomId(roomId)
                .senderId(userId)
                .content(String.valueOf(isOnline))
                .build();

        return redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC_NAME, statusMessage)
                .then();
    }

    public Mono<Void> publishTypingStatus(String roomId, Long userId, String isTyping) {
        ChatMessageDto typingStatus = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.TYPING)
                .roomId(roomId)
                .senderId(userId)
                .content(isTyping) // "true" or "false"
                .build();

        return redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC_NAME, typingStatus)
                .then();
    }
}