# AUTH_SERVICE.md

## 1. 개요

`auth-service`는 LinkFolio MSA의 **인증/인가**를 총괄하는 핵심 마이크로서비스이다.

주요 책임은 다음과 같다:
1.  **인증 처리**: 자체(Local) 로그인 및 소셜(OAuth2) 로그인을 처리한다.
2.  **토큰 발급**: 인증 성공 시, `apigateway-service`에서 검증할 JWT(Access Token, Refresh Token)를 생성하여 발급한다.
3.  **SAGA 트랜잭션 주관**: 회원가입 시, 분산 트랜잭션(SAGA)의 **시작점(Coordinator)** 역할을 수행한다. `AuthUserEntity`를 `PENDING` 상태로 먼저 생성하고 Kafka 이벤트를 발행하여 `user-service`의 프로필 생성을 요청한다.

---

## 2. 핵심 기능

* Spring Security를 이용한 자체 로그인(ID/PW) 인증
* OAuth2 (Google, Naver, Kakao) 소셜 로그인 인증
* Refresh Token Rotation (RTR) 전략을 사용한 JWT 발급 및 재발급 (`/auth/refresh`)
* Kafka SAGA 패턴을 이용한 분산 회원가입 트랜잭션 (Auth/User)
* Redis를 이용한 이메일 인증 코드 관리 (회원가입/비밀번호 재설정)
* Redis를 이용한 OAuth2 `state` 관리 (Stateless 인증)
* 계정 관리 (아이디 찾기, 비밀번호 변경/재설정)

---

## 3. 인증 흐름 (Authentication Flow)

`SecurityConfig`를 중심으로 자체 로그인과 소셜 로그인이 분리되어 처리된다.

### 3.1. 자체 로그인 (Local Login)

1.  클라이언트가 `/auth/login` 엔드포인트로 ID/PW를 POST 요청한다.
2.  `CustomAuthenticationFilter`가 이 요청을 가로채 `UsernamePasswordAuthenticationToken`을 생성하여 `AuthenticationManager`에 인증을 위임한다.
3.  `AuthenticationManager`는 `CustomUserDetailsService`를 호출하여 사용자를 조회한다.
4.  `CustomUserDetailsService`는 `AuthUserRepository`에서 `username` 기준으로 사용자를 조회한다.
5.  **[SAGA 연동]** 이때, `AuthUserEntity`의 `status`가 `COMPLETED`가 아닌 경우(예: `PENDING` 또는 `CANCELLED`) 로그인을 차단하여 회원가입 SAGA가 완료된 사용자만 인증을 허용한다.
6.  인증 성공 시, `LocalLoginSuccessHandler`가 호출된다.
7.  `LocalLoginSuccessHandler`는 `JwtTokenProvider`를 통해 Access/Refresh Token을 발급받는다.
8.  Access Token은 JSON 응답 본문에, Refresh Token은 HttpOnly 쿠키(`refresh_token`)에 담아 클라이언트에 반환한다.
9.  Refresh Token은 `RefreshTokenService`를 통해 Redis에 `RT:<userId>` 키로 저장된다.

### 3.2. 소셜 로그인 (OAuth2)

1.  `SecurityConfig`에 정의된 대로 `CustomOAuth2UserService`가 인증을 처리한다.
2.  **[Stateless]** `RedisBasedAuthorizationRequestRepository`를 사용하여 OAuth2 인증 요청의 `state` 값을 세션 대신 Redis에 저장함으로써, 다중화(Replica) 환경에서도 일관성을 유지한다.
3.  `CustomOAuth2UserService`는 `전략 패턴(Strategy Pattern)`을 사용한다. `Map<String, OAuth2AttributeParser>` 빈을 주입받아, `registrationId` (google, naver, kakao)에 맞는 파서(Parser)를 동적으로 선택하여 공급자별 응답을 `OAuthAttributes` DTO로 표준화한다.
4.  `saveOrUpdate` 로직을 통해 기가입자인지 확인하고, 신규 가입자일 경우 `자체 로그인과 동일한 SAGA 트랜잭션(Kafka 이벤트 발행)`을 시작한다.
5.  인증 성공 시, `OAuth2LoginSuccessHandler`가 호출된다.
6.  이 핸들러는 토큰 발급 및 Redis 저장, 쿠키 설정을 수행한 후, Access Token을 쿼리 파라미터(`?token=...`)로 붙여 프론트엔드 URL로 리디렉션시킨다.

### 3.3. JWT 발급 및 재발급 (RTR)

* **`JwtTokenProvider`**:
    * **Access Token**: `userId(sub)`, `email`, `role`을 포함하며 만료 시간이 짧다.
    * **Refresh Token**: `userId(sub)`만 포함하며 만료 시간이 길다.
* **`RefreshTokenService`** (RTR 구현):
    * `/auth/refresh` 요청 시, 쿠키의 Refresh Token과 Redis에 저장된 `RT:<userId>`의 토큰 값을 비교한다.
    * 두 토큰이 일치하지 않으면, 토큰 탈취 시도로 간주하여 Redis의 토큰을 삭제하고 `REFRESH_TOKEN_MISMATCH` 오류를 반환한다.
    * 두 토큰이 일치하면, **새로운 Access Token**과 **새로운 Refresh Token**을 모두 재발급한다.
    * 새 Refresh Token을 Redis에 덮어쓰고, 새 쿠키를 클라이언트에 전송하여 토큰을 '회전(Rotate)'시킨다.

---

## 4. SAGA (회원가입) 트랜잭션

`auth-service`와 `user-service`는 Kafka를 통해 회원가입 트랜잭션을 처리한다. `auth-service`는 이 SAGA의 주관사(Coordinator) 역할을 한다.

### 4.1. SAGA 시작 (AuthService.signUp)

`AuthService.signUp` 메서드는 `@Transactional`로 선언되어 있다.

1.  `EmailVerificationService`를 통해 이메일이 인증 완료(`VE:`) 상태인지 Redis에서 확인한다.
2.  `AuthUserEntity`를 `AuthStatus.PENDING` 상태로 생성하여 Auth DB에 저장한다.
3.  `UserEventProducer`를 통해 `UserRegistrationRequestedEvent` (프로필 생성 요청) 이벤트를 Kafka로 발행한다.
4.  만약 Kafka 발행이 실패하면 `UserEventProducer`가 `BusinessException`을 발생시키고, `@Transactional` 어노테이션에 의해 2번에서 저장된 `AuthUserEntity`(PENDING 상태)가 롤백된다.

### 4.2. SAGA 응답 처리 (AuthEventHandler)

`AuthEventHandler`는 `user-service`의 처리 결과를 Kafka로부터 수신한다.

* **`handleProfileCreationSuccess` (성공)**: `UserProfileCreationSuccessEvent` 수신 시, `userId`로 `PENDING` 상태의 `AuthUserEntity`를 찾아 상태를 `COMPLETED`로 변경한다.
* **`handleProfileCreationFailure` (보상)**: `UserProfileCreationFailureEvent` 수신 시 (SAGA 롤백), `AuthUserEntity`의 상태를 `CANCELLED`로 변경한다.
* **`handleUserProfileUpdate` (동기화)**: 회원가입 이후 `user-service`에서 사용자 정보(예: 이름)가 변경될 경우, `UserProfilePublishedEvent`를 수신하여 `auth-service` DB의 `name` 필드도 일관성 있게 업데이트한다.

---

## 5. 주요 기능 상세 (Redis 활용)

`auth-service`는 `RedisConfig`를 통해 Redis를 광범위하게 사용한다.

* **RefreshTokenService**: `RT:<userId>` 키로 Refresh Token을 저장 (RTR에 사용).
* **RedisBasedAuthorizationRequestRepository**: `OAUTH2_REQ:<state>` 키로 OAuth2 인증 요청 상태 저장.
* **EmailVerificationService**:
    * `VC:<email>`: 회원가입 인증 코드 (3분 TTL)
    * `VE:<email>`: 회원가입 인증 완료 상태 (3분 TTL)
    * `PW_RESET:<email>`: 비밀번호 재설정 인증 코드 (3분 TTL)
    * `PW_VERIFIED:<email>`: 비밀번호 재설정 인증 완료 상태 (6분 TTL)

---

## 6. 의존성 관리 (pom.xml)

`auth-service`는 인증/인가 및 SAGA 주관에 필요한 다양한 의존성을 포함한다.

* `spring-boot-starter-security`: Spring Security 핵심.
* `spring-boot-starter-oauth2-client`: 소셜 로그인 기능.
* `jjwt-api`, `jjwt-impl`, `jjwt-jackson`: JWT 생성, 파싱, 검증.
* `spring-boot-starter-data-redis`: Refresh Token, OAuth2 State, 인증 코드 저장.
* `spring-boot-starter-mail`: 이메일 인증 코드 발송.
* `spring-kafka`: SAGA 트랜잭션 이벤트 발행 및 수신.
* `common-module`: 공통 DTO, 예외, Enum 공유.