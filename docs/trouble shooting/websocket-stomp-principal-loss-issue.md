# WebSocket(STOMP) 연결 시 Principal 객체 상실 및 인증 처리 트러블슈팅

* **🗓️ 발생 일시:** 2025년 11월 19일
* **👨‍💻 담당자:** 허준형
* **🏷️ 관련 서비스:** `chat-service`

---

## 🐛 이슈 발생

### 현상 요약

채팅 서비스 구현 중, 클라이언트가 WebSocket 연결을 맺고 메시지를 보낼 때 서버 측에서 **사용자 식별(User Principal)이 불가능한 현상**이 발생함.
구체적으로는 `SimpMessagingTemplate.convertAndSendToUser()`를 사용하여 특정 사용자에게 메시지를 전송하려 했으나, 대상 사용자를 찾지 못하거나, 메시지 핸들러에서 `Principal` 객체가 `null`로 조회됨.

### 재현 순서

1.  Gateway를 거쳐 `X-User-Id` 헤더를 포함해 웹소켓 연결 요청 (`ws://.../ws-stomp`).
2.  연결은 성공(101 Switching Protocols)하나, STOMP `CONNECT` 프레임 단계에서 인증 정보가 SecurityContext에 제대로 바인딩되지 않음.
3.  서버 로그 확인 시 `Principal` 정보가 없거나 '익명 사용자'로 인식됨.

---

## 🧐 원인 분석

* **WebSocket과 HTTP의 분리:** 초기 Handshake 요청은 HTTP로 이루어지지만, 이후 업그레이드된 WebSocket 세션은 기존 HTTP 요청의 헤더나 SecurityContext를 그대로 승계하지 않음.
* **STOMP 프로토콜의 헤더 제약:** 표준 WebSocket API(JS) 사용 시, 연결 요청 헤더에 커스텀 토큰(`Authorization` 등)을 자유롭게 넣기 어려운 브라우저 제약이 있음.
* **Spring Security의 동작 방식:** Spring Security는 기본적으로 ThreadLocal을 사용하여 인증 정보를 관리하는데, 비동기적인 WebSocket 메시지 처리 스레드에는 이 정보가 전파되지 않음. 따라서 `ChannelInterceptor`에서 명시적으로 `accessor.setUser(principal)`를 해주지 않으면 세션은 인증되지 않은 상태로 남게 됨.

---

## ✅ 해결 방안

**Handshake 단계에서 사용자 정보를 세션 속성으로 넘기고, STOMP 연결 시점에 이를 꺼내어 Principal을 수입 주입하는 방식**으로 해결함.

### 조치 1: `HttpHandshakeInterceptor` 구현

Handshake 과정에서 HTTP 헤더(`X-User-Id`)에 있는 사용자 식별자를 가로채서 WebSocket 세션 속성(`attributes`)에 저장함.

```
// HttpHandshakeInterceptor.java
@Override
public boolean beforeHandshake(ServerHttpRequest request, ..., Map<String, Object> attributes) {
    // Gateway가 넣어준 X-User-Id 헤더 추출
    String userId = request.getHeaders().getFirst("X-User-Id");
    if (userId != null) {
        // WebSocket 세션 속성에 저장
        attributes.put("X-User-Id", userId);
    }
    return true;
}
```

### 조치 2: `StompHandler`(ChannelInterceptor) 구현
실제 STOMP CONNECT 명령이 들어왔을 때, 앞서 저장해둔 세션 속성에서 X-User-Id를 꺼내 UsernamePasswordAuthenticationToken을 생성하고 주입함.

```
// StompHandler.java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        
        // 세션에 저장된 사용자 ID 확인
        if (sessionAttributes != null && sessionAttributes.containsKey("X-User-Id")) {
            String userIdStr = (String) sessionAttributes.get("X-User-Id");
            Long userId = Long.parseLong(userIdStr);

            // Principal 생성 및 주입 (핵심)
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userId, null, null);
            accessor.setUser(authentication);
        }
    }
    return message;
}
```
---

## 📝 후속 조치 및 교훈

- 프로토콜 간 상태 공유: HTTP와 WebSocket은 서로 다른 라이프사이클을 가지므로, HandshakeInterceptor를 통해 데이터를 명시적으로 전달해야 함을 확인함.
- Interceptor의 역할 분담: `HandshakeInterceptor`는 'HTTP -> WebSocket 속성' 전달을, `ChannelInterceptor(StompHandler)`는 'WebSocket 속성 -> Security Principal' 변환을 담당하도록 역할을 명확히 분리함.