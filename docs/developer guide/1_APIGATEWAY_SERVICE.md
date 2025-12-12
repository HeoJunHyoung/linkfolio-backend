## 1. 개요
- `apigateway-service`는 LinkFolio 백엔드 마이크로서비스 아키텍처(MSA)의 **단일 진입점(Single Point of Entry)** 역할을 수행하는 API 게이트웨이 서비스이다.
- 외부(Web, App)에서의 모든 요청은 **Ngrok 터널과 Ingress Controller**를 거쳐 최종적으로 이 게이트웨이에 도달한다.
- 요청을 인증하고 내부의 적절한 마이크로서비스(예: `user-service`, `auth-service`, `portfolio-service`, `chat-service` 등)로 라우팅(Routing)하는 책임을 진다.
- 주요 기술 스택으로는 Spring Cloud Gateway를 사용하여 비동기/논블로킹(Non-Blocking) 방식의 반응형(Reactive) 시스템으로 구축되었다.

---

## 2. 핵심 설정 (application.yml)

### 2.1. forward-headers-strategy: NATIVE

`application.yml`에 설정된 `server.forward-headers-strategy: NATIVE`는 게이트웨이가 자신 앞단에 위치한 다중 리버스 프록시 계층(Ngrok 터널링 → Ingress Nginx)을 신뢰하고, 원본 요청 정보를 올바르게 인식하도록 지시하는 핵심 설정이다.

- **필요성 (현재 인프라 환경 고려)**:
  1.  **트래픽 흐름**: 외부 클라이언트가 `https://impressionless-connaturally-jonie.ngrok-free.dev` (HTTPS)로 요청을 보낸다.
  2.  **Ngrok**: Ngrok 서버가 HTTPS 요청을 받아 복호화(SSL Termination)한 후, 로컬의 **Ingress Controller**로 전달한다.
  3.  **Ingress Nginx**: Ingress는 클러스터 내부의 **API Gateway**로 트래픽을 라우팅한다.
  4.  **결과**: 물리적으로 API Gateway가 받는 요청은 암호화되지 않은 **내부 HTTP** 요청이며, 호스트 정보 또한 내부 클러스터 IP로 변경된 상태이다.

- **기본 문제**: 이 설정을 사용하지 않으면(`NONE`), 게이트웨이는 자신이 `http://` 프로토콜 위에서 동작한다고 착각하며, 요청한 호스트 또한 Ngrok 도메인이 아닌 내부 Ingress나 Pod의 IP로 인식하게 된다.

- **동작**: `NATIVE` 전략은 앞단의 프록시(Ngrok, Ingress)가 헤더에 추가해준 표준 `X-Forwarded-*` 정보를 분석하여 Spring WebFlux의 컨텍스트를 재구성한다.
  * `X-Forwarded-Proto`: `https` (Ngrok이 받았던 원본 프로토콜)
  * `X-Forwarded-For`: 실제 클라이언트의 공인 IP
  * `X-Forwarded-Host`: `impressionless-connaturally-jonie.ngrok-free.dev` (사용자가 실제 호출한 도메인)

- **미사용 시 문제점**:
  1.  **Swagger UI 오류 (Mixed Content)**: 게이트웨이가 API 명세 문서(`api-docs`)의 URL을 `http://...`로 생성하여 반환한다. 브라우저는 `https`로 접속한 Swagger 페이지에서 `http` 요청을 보내는 것을 보안상 차단하므로 "Network Error"가 발생한다.
  2.  **리디렉션 불일치**: OAuth2 로그인 후 리디렉션 시 `Location` 헤더에 `http` 주소가 담겨, 프로토콜 불일치로 인한 로그인 실패가 발생한다.
  3.  **로그 식별 불가**: 모든 요청의 소스 IP가 Ingress Controller의 내부 IP로 기록되어, 실제 사용자 추적 및 어뷰징 차단이 불가능해진다.

### 2.2. CORS (Cross-Origin Resource Sharing)
`globalcors` 설정을 통해 모든 경로(`[/**]`)에 대해 허용된 오리진(Origin)에서의 요청을 수락한다.
- **주요 허용 오리진**:
  - Ngrok 터널링 주소 (`https://impressionless-connaturally-jonie.ngrok-free.dev`)
  - 로컬 개발 환경 (`localhost:3000`, `127.0.0.1:5500`)
  - 배포된 프론트엔드 (`vercel.app` 도메인)
- `allow-credentials: true` 설정을 통해 인증 토큰(Cookie, Header)을 포함한 요청을 허용한다.

### 2.3. 라우팅 (Routes)
Ingress를 통해 들어온 요청을 실제 비즈니스 로직을 수행하는 마이크로서비스로 중계하는 규칙이다.

- `user-service-route`: `/user-service/**` → `http://user-service:80` (K8s Service DNS)
- `auth-service-route`: `/auth-service/**` → `http://auth-service:80`
- `portfolio-service-route`: `/portfolio-service/**` → `http://portfolio-service:80`
- `chat-service-route`: `/chat-service/**` → `http://chat-service:80` (HTTP API)
- `chat-service-ws-route`: `/ws-chat/**` → `http://chat-service:80` (WebSocket 연결)
- `community-service-route`: `/community-service/**` → `http://community-service:80`
- `support-service-route`: `/support-service/**` → `http://support-service:80`

### 2.4. 인증 제외 경로 (Excluded URLs)

`app.gateway.excluded-urls` 목록에 포함된 경로는 `AuthorizationHeaderFilter`의 인증 검사를 통과(bypass)한다.

주로 회원가입, 로그인, 토큰 재발급, OAuth2 처리, Swagger 문서 조회(`v3/api-docs`), 그리고 비로그인 사용자도 접근 가능한 포트폴리오/게시글 조회 API가 이에 해당한다.

---

## 3. 인증 필터 (AuthorizationHeaderFilter.java)
이 필터는 게이트웨이의 핵심 보안 로직을 담당한다.

### 3.1. 필요성
MSA 구조에서 각 서비스(`user-service`, `portfolio-service` 등)가 개별적으로 JWT 토큰을 검증하는 것은 비효율적이다. 게이트웨이가 앞단에서 **중앙 인증 지점(Authentication Offloader)** 역할을 맡아, 유효한 JWT 토큰을 가진 요청만 내부 서비스로 전달한다. 내부 서비스들은 게이트웨이를 거쳐 온 요청을 '신뢰'할 수 있게 된다.

### 3.2. 동작 원리 및 흐름
`GlobalFilter`로 구현되었으며, `Ordered.HIGHEST_PRECEDENCE` (최고 우선순위)를 가져 다른 어떤 필터보다 먼저 실행된다.

1. **요청 경로 확인**: 현재 요청의 경로(path)를 가져온다.

2. **화이트리스트 검사**: `isPatchExcluded` 메서드를 호출하여, `excluded-urls`에 등록된 경로인지 확인한다. 등록된 경로라면 인증 절차 없이 즉시 통과시킨다.

3. **헤더 존재 여부 검사**: 화이트리스트에 없는 경로일 경우, `Authorization` 헤더(또는 쿼리 파라미터 `token`)가 있는지 확인한다. 없으면 `MISSING_AUTH_HEADER` 예외를 발생시킨다.

4. **JWT 추출**: `Bearer ` 접두사를 제거하고 순수 JWT 토큰을 추출한다.

5. **JWT 검증 및 파싱**: 서명 키(Secret Key)를 사용하여 토큰의 무결성을 검증하고 Payload(Claims)를 추출한다. 유효하지 않은 토큰이면 `INVALID_JWT_TOKEN` 예외를 발생시킨다.

6. **Claims 정보 추출**: Claims에서 `userId`, `email`, `role` 정보를 추출한다.

7. **내부 헤더 주입**: `buildInternalRequest` 메서드를 호출하여 새로운 요청 객체를 생성한다. (보안 핵심, 3.3절 참고)

8. **요청 전달**: 인증된 사용자 정보를 담은 **새로운 요청 객체(ServerHttpRequest)** 를 생성하여 다음 필터 체인으로 전달한다.  
   이때 게이트웨이는 원본 요청의 `Authorization` 헤더를 제거하고, 검증을 완료한 JWT의 **Payload만 내부 인증 헤더**로 변환하여 전달한다.

  - 제거되는 헤더
    ```http
    Authorization: Bearer <JWT>
    ```

  - 내부 서비스로 전달되는 헤더
    ```
    X-User-Id: <userId>
    X-User-Email: <email>
    X-User-Role: <role>
    ```

   즉, 게이트웨이는 **JWT 전체를 Downstream 서비스로 전달하지 않고**, 검증된 Payload만 안전한 내부 헤더에 실어서 전달한다.
   Downstream 서비스(`user-service`, `portfolio-service` 등)는 JWT 파싱을 하지 않아도 되고, 다음과 같이 단순히 헤더만 읽어도 인증 정보를 신뢰할 수 있다:

   ```
   @GetMapping("/me")
   public UserProfile getMyInfo(
       @RequestHeader("X-User-Id") String userId,
       @RequestHeader("X-User-Email") String email,
       @RequestHeader("X-User-Role") String role
   ) {
       // 게이트웨이가 보증한 인증 정보
       ...
   }
  ```

### 3.3. 스푸핑(Spoofing) 공격 방지
- **문제 상황**: 내부 서비스들은 `X-User-Id`와 같은 커스텀 헤더를 통해 사용자 정보를 식별한다. 만약 악의적인 사용자가 Ngrok을 통해 요청을 보낼 때, 자신의 토큰과 함께 위조된 `X-User-Id: admin` 헤더를 강제로 삽입해서 보낸다면, 게이트웨이가 이를 단순히 통과시킬 경우 권한 상승(Privilege Escalation) 문제가 발생할 수 있다.

- **해결 방안 (헤더 재작성 전략)**: `buildInternalRequest` 메서드는 이 문제를 원천 차단한다.
  1. 요청을 변조 모드(`mutate`)로 전환한다.
  2. **Remove**: 외부에서 주입되었을 가능성이 있는 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 **무조건 삭제**한다. 또한 내부 통신에는 불필요한 `Authorization` 헤더도 제거한다.
  3. **Add**: 오직 서버가 검증한 JWT 토큰에서 직접 파싱한 신뢰할 수 있는 값만을 사용하여 `X-User-*` 헤더를 새롭게 추가한다.

| **Q&A**: "JWT에서 값을 꺼내 `add`만 하면 덮어써지지 않나요?"
| **A**: HTTP 헤더는 스펙상 **다중 값(Multi-valued)**을 가질 수 있습니다. `remove` 없이 `add`를 하면 `X-User-Id: [fake_id, real_id]` 처럼 두 값이 모두 전달될 수 있으며, 내부 서비스가 어떤 값을 읽을지 보장할 수 없습니다. 따라서 **반드시 `remove` 후 `add`를 수행해야 안전합니다.**