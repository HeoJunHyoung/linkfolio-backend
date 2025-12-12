package com.example.chatservice.config;

import com.example.chatservice.handler.HttpHandshakeInterceptor;
import com.example.chatservice.handler.StompHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * <h3>WebSocket + STOMP 전체 흐름과 설정 설명</h3>
 * <hr>
 * <br>
 *
 * <h3>[전체적인 큰 흐름]</h3>
 * <p>WebSocket + STOMP 구조는 크게 다음 3단계로 동작합니다.</p>
 * <ol>
 * <li><b>HTTP → WebSocket 업그레이드 단계 (Handshake)</b>
 * <ul>
 * <li>브라우저가 일반 HTTP 요청을 보내고 서버가 이를 WebSocket 프로토콜로 업그레이드합니다.</li>
 * <li>이 단계에서 인증(JWT 검증)이나 초기 세션 값 저장이 이루어집니다.</li>
 * </ul>
 * </li>
 * <li><b>클라이언트 → 서버로 메시지 전송 ("/app" 경로 사용)</b>
 * <ul>
 * <li>프론트엔드에서 <code>/app</code>으로 시작하는 주소로 메시지를 보내면, 서버의 <code>@MessageMapping</code> 메서드가 이 메시지를 처리합니다.</li>
 * </ul>
 * </li>
 * <li><b>서버 → 클라이언트에게 메시지 브로드캐스트 ("/topic" 경로 사용)</b>
 * <ul>
 * <li>서버(또는 Redis Subscriber)가 <code>/topic</code>으로 시작하는 경로로 메시지를 발행하면, 해당 토픽을 구독중인 클라이언트들은 실시간으로 메시지를 전달받습니다.</li>
 * </ul>
 * </li>
 * </ol>
 * <br>
 * <hr>
 *
 * <h3>1. registerStompEndpoints (WebSocket 초기 연결 과정)</h3>
 * <ul>
 * <li><code>registry.addEndpoint("/ws-chat")</code>
 * <ul>
 * <li>브라우저가 WebSocket을 처음 연결할 때 호출하는 URL입니다.</li>
 * <li>즉, "웹소켓 연결 요청은 <b>/ws-chat</b> 주소로 하십시오"라는 의미입니다.</li>
 * </ul>
 * </li>
 * <li><code>setAllowedOriginPatterns("*")</code>
 * <ul>
 * <li>특정 도메인만 허용하는 CORS와 비슷한 개념이며, 어떤 프론트엔드(origin)에서도 접속 가능하도록 허용합니다.</li>
 * </ul>
 * </li>
 * <li><code>addInterceptors(httpHandshakeInterceptor)</code>
 * <ul>
 * <li>Handshake Interceptor는 WebSocket 연결이 성립되기 이전, 즉 <b>HTTP 요청 단계</b>에서 실행됩니다.</li>
 * <li><b>WebSocket 연결 흐름:</b>
 * <ol>
 * <li>브라우저가 <code>/ws-chat</code> 주소로 일반 HTTP 요청을 보냅니다.</li>
 * <li>서버는 이 요청을 WebSocket 프로토콜로 업그레이드합니다.</li>
 * <li>이 과정에서 JWT 인증이나 유저 정보 세팅, 접근 차단 등이 가능합니다.</li>
 * </ol>
 * </li>
 * <li><b>Handshake 단계에서 수행하는 작업:</b>
 * <ul>
 * <li>HTTP Header 또는 URL Query에서 토큰 추출</li>
 * <li>JWT 검증 후 유저 ID를 WebSocket 세션에 저장</li>
 * <li>인증 실패 시 WebSocket 연결 자체를 차단</li>
 * </ul>
 * </li>
 * <li>즉, <b>"WebSocket 연결이 되기 전에 인증을 통과한 사용자만 입장시키는 문지기 역할"</b>을 합니다.</li>
 * </ul>
 * </li>
 * </ul>
 * <br>
 * <hr>
 *
 * <h3>2. configureMessageBroker (STOMP 주소 체계 설정)</h3>
 * <p>STOMP는 '주소 기반 라우팅'을 하기 때문에, 메시지를 보낼 때 사용하는 주소와 받을 때 사용하는 주소가 다릅니다.</p>
 * <ul>
 * <li><b>(1) setApplicationDestinationPrefixes("/app")</b>
 * <ul>
 * <li>클라이언트 → 서버로 메시지를 보낼 때 사용하는 prefix입니다.</li>
 * <li>예: <code>client.send("/app/chat/send", payload)</code></li>
 * <li>서버에서는 <code>@MessageMapping("/chat/send")</code> 로 이 메시지를 받습니다.</li>
 * <li>즉, <code>/app</code>으로 시작하면 "컨트롤러 메서드로 메시지를 보내라"는 규칙이 설정된 것입니다.</li>
 * <li>HTTP의 <code>@PostMapping</code> 같은 개념의 <b>"WebSocket용 Controller 경로"</b>라고 보면 됩니다.</li>
 * </ul>
 * </li>
 * <li><b>(2) enableSimpleBroker("/topic", "/queue")</b>
 * <ul>
 * <li>서버 → 클라이언트로 메시지를 전달할 때 사용하는 prefix입니다.</li>
 * <li><code>/topic</code> : 여러 사용자에게 메시지를 뿌릴 때 (Pub/Sub 방식)</li>
 * <li><code>/queue</code> : 1:1 개인 메시지 전용</li>
 * <li>예: 서버에서 <code>/topic/chatroom/123</code> 로 메시지를 발행하면, <code>/topic/chatroom/123</code> 을 구독한 클라이언트들이 모두 메시지를 받습니다.</li>
 * <li>즉, <b>"서버가 메시지를 어떤 주소로 발행할지"</b>를 정하는 이름공간(prefix)입니다.</li>
 * </ul>
 * </li>
 * </ul>
 * <br>
 * <hr>
 *
 * <h3>3. configureClientInboundChannel (서버가 메시지를 받는 구간 Interceptor)</h3>
 * <ul>
 * <li>이 부분은 Handshake 인터셉터와는 타이밍이 다릅니다.</li>
 * <li>Handshake는 WebSocket 연결 '이전' HTTP 단계에서 작동하지만, ClientInboundChannel 인터셉터는 <b>WebSocket이 이미 연결된 후</b> 클라이언트의 STOMP Frame이 서버로 들어올 때마다 작동합니다.</li>
 * <li><b>Interceptor가 가로채는 STOMP Command 예:</b>
 * <ul>
 * <li>CONNECT</li>
 * <li>SEND</li>
 * <li>SUBSCRIBE</li>
 * <li>DISCONNECT</li>
 * </ul>
 * </li>
 * <li><b>왜 필요한가?</b>
 * <ul>
 * <li>Handshake에서 인증을 통과했다고 해도 이후에 문제가 생길 수 있습니다.</li>
 * <li>예: X 사용자가 Y 채팅방 구독 주소에 SUBSCRIBE 요청을 보내는 경우, 권한 없는 채팅방에 SEND 요청을 보내는 경우, CONNECT 단계에서 토큰이 만료되었거나 변조된 경우</li>
 * <li>따라서 WebSocket 연결이 성립된 후에도 <b>"모든 행동을 계속 검사"</b>해야 합니다.</li>
 * </ul>
 * </li>
 * <li><b>Interceptor에서 수행 가능한 작업:</b>
 * <ul>
 * <li>SUBSCRIBE 요청 시 사용자가 자기 채팅방만 구독하는지 체크</li>
 * <li>SEND 요청 시 메시지 발송 권한 검증</li>
 * <li>CONNECT 요청 시 JWT 재검증</li>
 * <li>비정상적인 STOMP Frame 요청 차단</li>
 * </ul>
 * </li>
 * <li>즉, <b>"WebSocket 연결 후에도 지속적으로 보안을 검사하는 필터"</b>라고 할 수 있습니다.</li>
 * </ul>
 * <br>
 * <hr>
 *
 * <h3>[전체 메시지 흐름 요약]</h3>
 * <ol>
 * <li><b>WebSocket 연결</b>
 * <ul>
 * <li>프론트엔드: <code>/ws-chat</code> 연결 시도</li>
 * <li>Handshake Interceptor가 JWT 검사</li>
 * <li>성공 시 WebSocket 채널 생성</li>
 * </ul>
 * </li>
 * <li><b>메시지 보내기 (프론트 → 서버)</b>
 * <ul>
 * <li>프론트: <code>/app/채팅방/send</code> 로 메시지 전송</li>
 * <li>서버의 <code>@MessageMapping</code> 메서드 실행</li>
 * <li>서버에서 Redis Publish 등 처리</li>
 * <li>Subscriber가 메시지를 <code>/topic/**</code> 로 발행</li>
 * </ul>
 * </li>
 * <li><b>메시지 받기 (서버 → 프론트)</b>
 * <ul>
 * <li>클라이언트는 미리 <code>/topic/채팅방/123</code> 을 구독합니다.</li>
 * <li>서버가 해당 토픽으로 메시지를 발행하면 실시간으로 전달됩니다.</li>
 * </ul>
 * </li>
 * </ol>
 * <hr>
 * <p>이 설정 전체는 웹소켓 기반의 실시간 메시징 기능을 안전하게 구성하기 위한 핵심 로직입니다.</p>
 */


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;
    private final HttpHandshakeInterceptor httpHandshakeInterceptor;


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(httpHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // STOMP 메시지가 들어올 때(CONNECT, SEND 등) 가로채서 인증(JWT 등)을 처리하는 인터셉터 등록
        registration.interceptors(stompHandler);
    }
}