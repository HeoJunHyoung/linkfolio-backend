package com.example.chatservice.handler;

import com.example.chatservice.config.RedisConfig;
import com.example.chatservice.dto.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;

/**
 * Redis Pub/Sub 메시지를 구독하고 WebSocket으로 전파하는 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ChatWebSocketHandler webSocketHandler; // 웹소켓 세션 관리 및 전송

    @PostConstruct
    public void init() {
        // 1. CHAT_TOPIC 구독 (실시간 채팅 메시지, TYPING, READ, ENTER/EXIT 상태 전파)
        redisTemplate.listenTo(ChannelTopic.of(RedisConfig.CHAT_TOPIC_NAME))
                .map(message -> {
                    // RedisTemplate에 Jackson2JsonRedisSerializer가 설정되어 있어,
                    // 메시지 값이 ChatMessageDto 객체로 역직렬화되어 수신됩니다.
                    return (ChatMessageDto) message.getMessage();
                })
                .flatMap(messageDto -> {
                    if (messageDto != null) {
                        // Redis Pub/Sub을 통해 수신된 메시지를 해당 방의 WebSocket 세션으로 전송
                        webSocketHandler.sendMessage(messageDto.getRoomId(), messageDto);
                    }
                    return Mono.empty();
                })
                .subscribe();

        // 2. GLOBAL_NOTIFY_TOPIC 구독 (채팅방 목록 갱신 이벤트)
        redisTemplate.listenTo(ChannelTopic.of(RedisConfig.GLOBAL_NOTIFY_TOPIC_NAME))
                .map(message -> message.getMessage())
                .flatMap(roomEntity -> {
                    // ChatRoomEntity가 업데이트되면, 해당 사용자들에게 목록 갱신 알림을 보냅니다.
                    // 이 로직은 ChatWebSocketHandler의 sendMessage(roomId, message)를 사용할 수 없습니다.
                    // 대신 Redis에 ChatRoomEntity를 발행하여, ChatWebSocketHandler가 아닌
                    // 이 Subscriber가 목록 갱신 이벤트를 처리하고, 사용자 세션에 직접 전파해야 합니다.
                    log.info("채팅방 목록 갱신 알림 수신: {}", roomEntity);

                    // TODO: ChatRoomEntity를 기반으로 각 user의 웹소켓 세션에 ROOM_UPDATE 메시지를 전파하는 로직 구현 필요

                    return Mono.empty();
                })
                .subscribe();
    }
}