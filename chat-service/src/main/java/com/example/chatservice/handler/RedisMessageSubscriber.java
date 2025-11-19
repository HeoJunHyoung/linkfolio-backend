// chat-service/src/main/java/com/example/chatservice/handler/RedisMessageSubscriber.java
package com.example.chatservice.handler;

import com.example.chatservice.config.RedisConfig;
import com.example.chatservice.dto.ChatMessageDto;
import com.example.chatservice.entity.ChatRoomEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import jakarta.annotation.PostConstruct;
import reactor.core.scheduler.Schedulers;

/**
 * Redis Pub/Sub 메시지를 구독하고 WebSocket으로 전파하는 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ChatWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // 1. CHAT_TOPIC 구독 (실시간 채팅 메시지, TYPING, READ, ENTER/EXIT 상태 전파)
        redisTemplate.listenTo(ChannelTopic.of(RedisConfig.CHAT_TOPIC_NAME))
                .map(message -> {
                    // Jackson2JsonRedisSerializer가 설정되어 있어 ChatMessageDto로 수신됩니다.
                    return (ChatMessageDto) message.getMessage();
                })
                .flatMap(messageDto -> {
                    if (messageDto != null) {
                        webSocketHandler.sendMessageToRoom(messageDto.getRoomId(), messageDto);
                    }
                    return Mono.empty();
                })
                .subscribe();

        // 2. GLOBAL_NOTIFY_TOPIC 구독 (채팅방 목록 갱신 이벤트)
        redisTemplate.listenTo(ChannelTopic.of(RedisConfig.GLOBAL_NOTIFY_TOPIC_NAME))
                .map(message -> {
                    // ChatRoomEntity로 수신됩니다.
                    Object receivedObject = message.getMessage();
                    // Redis Value Serializer가 Object.class를 사용하므로, 역직렬화된 타입 확인 후 캐스팅
                    if (receivedObject instanceof ChatRoomEntity) {
                        return (ChatRoomEntity) receivedObject;
                    }
                    // 역직렬화 실패 시 ObjectMapper를 통해 수동으로 변환 시도
                    return objectMapper.convertValue(receivedObject, ChatRoomEntity.class);
                })
                .flatMap(roomEntity -> {
                    log.info("채팅방 목록 갱신 알림 수신: RoomId {}", roomEntity.getId());

                    // 갱신 이벤트 DTO 생성
                    ChatMessageDto roomUpdateNotification = ChatMessageDto.builder()
                            .type(ChatMessageDto.MessageType.ROOM_UPDATE)
                            .roomId(roomEntity.getId())
                            .build();

                    // ChatRoom의 참여자 두 명에게 각각 알림 전송 (동시에 처리)
                    Mono<Void> notifyUser1 = Mono.fromRunnable(() ->
                            webSocketHandler.sendGlobalNotificationToUser(roomEntity.getUser1Id(), roomUpdateNotification)
                    ).subscribeOn(Schedulers.immediate()).then();

                    Mono<Void> notifyUser2 = Mono.fromRunnable(() ->
                            webSocketHandler.sendGlobalNotificationToUser(roomEntity.getUser2Id(), roomUpdateNotification)
                    ).subscribeOn(Schedulers.immediate()).then();

                    return Mono.when(notifyUser1, notifyUser2);
                })
                .subscribe(null, error -> log.error("GLOBAL_NOTIFY_TOPIC 구독 중 오류 발생", error));
    }
}