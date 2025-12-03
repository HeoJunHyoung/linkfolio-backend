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

### 3.4. Spring Security 설정 (`SecurityConfig.java`)

`SecurityConfig`는 `auth-service`의 모든 인증/인가 흐름을 정의하는 중추적인 파일이다.

1.  **세션 관리**: `sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` 설정을 통해 이 서비스가 HTTP 세션을 사용하지 않는 **Stateless** 서버임을 명시한다. 이는 JWT 기반 인증의 필수 요소이다.
2.  **경로 권한 (인가)**: `authorizeHttpRequests`를 통해 각 API 경로의 접근 권한을 설정한다.
    * `/auth/**`, `/oauth2/**` 등 인증 자체를 처리하는 모든 경로는 `permitAll()`로 허용된다.
    * 그 외의 모든 요청(`anyRequest()`)은 `authenticated()`로 설정되지만, `auth-service`는 게이트웨이의 내부 헤더 인증(`InternalHeaderAuthenticationFilter`)을 사용하지 않으므로, 사실상 모든 비인증 경로는 `permitAll()`로 열려있는 것과 유사하게 동작한다. (인증이 필요한 API는 `AuthController`의 `/password`, `/logout` 정도이다.)
3.  **자체 로그인 필터 체인**: `http.addFilter(authenticationFilter)`를 통해 `CustomAuthenticationFilter`를 등록한다. 이 필터는 `/auth/login` 경로의 요청을 전담하여 `CustomUserDetailsService` 및 `LocalLoginSuccessHandler`로 연결한다.
4.  **소셜 로그인(OAuth2) 필터 체인**: `http.oauth2Login()` 블록을 통해 소셜 로그인 흐름을 커스터마이징한다.
    * `authorizationRequestRepository(redisBasedAuthorizationRequestRepository)`: `state` 값을 세션 대신 Redis에 저장하도록 설정한다.
    * `userService(customOAuth2UserService)`: 공급자로부터 사용자 정보를 받아온 후, `CustomOAuth2UserService`를 실행하여 SAGA 트랜잭션을 포함한 회원가입/로그인 로직을 수행하도록 한다.
    * `successHandler(oAuth2LoginSuccessHandler)`: 인증 성공 후, `OAuth2LoginSuccessHandler`를 실행하여 JWT를 발급하고 프론트엔드로 리디렉션한다.


---

## 4. SAGA (회원가입) 트랜잭션

`auth-service`와 `user-service`는 Kafka를 통해 회원가입 트랜잭션을 처리한다. `auth-service`는 이 SAGA의 주관사(Coordinator) 역할을 한다.

### 4.1. SAGA 시작 (AuthService.signUp)

`AuthService.signUp` 메서드는 `@Transactional`로 선언되어 있으며 **Outbox Pattern**을 사용한다.

1.  `EmailVerificationService`를 통해 이메일이 인증 완료(`VE:`) 상태인지 Redis에서 확인한다.
2.  `AuthUserEntity`를 `AuthStatus.PENDING` 상태로 생성하여 Auth DB에 저장한다.
3.  `UserRegistrationRequestedEvent` 이벤트를 JSON으로 변환하여 `OutboxEntity`에 저장한다.
4.  트랜잭션이 커밋되면, **Debezium(CDC)**이 `outbox` 테이블의 변경을 감지하여 Kafka로 이벤트를 자동 발행한다.
5.  이를 통해 DB 저장과 메시지 발행의 원자성(Atomicity)을 보장한다.

### 4.2. SAGA 응답 처리 (AuthEventHandler)

`AuthEventHandler`는 `user-service`의 처리 결과를 Kafka로부터 수신한다. 이때 Java 객체가 아닌 **CDC 이벤트(Avro GenericRecord)**를 직접 수신한다.

* **`handleUserProfileEvent` (성공 및 동기화)**:
    * 토픽: `user_db_server.user_db.user_profile` (user-service DB 변경 로그)
    * `user-service`가 프로필을 `COMPLETED` 상태로 저장하면 이 이벤트를 수신한다.
    * `userId`로 `PENDING` 상태의 `AuthUserEntity`를 찾아 상태를 `COMPLETED`로 변경하여 회원가입을 완료한다.
    * 또한, 사용자 이름(`name`) 등이 변경된 경우 `AuthUserEntity` 정보를 동기화한다.
* **(실패 시)**: `UserProfileCreationFailureEvent` 수신 시 보상 트랜잭션을 수행하여 상태를 `CANCELLED`로 변경한다. (별도 토픽 사용)

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

### 5.1. Redis 설정 (`RedisConfig.java`)

`auth-service`의 `RedisConfig`는 다른 서비스(예: `user-service`)와 다른 직렬화 방식을 사용한다.

* **Key Serializer**: `StringRedisSerializer`를 사용하여 Redis의 키가 `RT:1`, `VC:test@...`처럼 인간이 읽을 수 있는 문자열로 저장되도록 한다.
* **Value Serializer**: `JdkSerializationRedisSerializer`를 사용한다.

**JSON 직렬화를 사용하지 못하는 이유:**
`RefreshTokenService`나 `EmailVerificationService`는 값으로 단순 `String`을 저장하므로 어떤 직렬화 방식을 사용해도 무방하다.

하지만 `RedisBasedAuthorizationRequestRepository`는 OAuth2 인증 요청 정보를 담고 있는 `OAuth2AuthorizationRequest` 객체 자체를 Redis에 저장해야 한다. 이 객체는 Spring Security가 제공하는 복잡한 객체이며, 단순 POJO가 아니기 때문에 **JSON 직렬화(예: `GenericJackson2JsonRedisSerializer`)가 불가능하다.**

`OAuth2AuthorizationRequest` 객체는 `java.io.Serializable` 인터페이스를 구현하고 있으므로, **Java의 기본 직렬화 방식**을 사용해야만 한다. `JdkSerializationRedisSerializer`가 바로 이 역할을 수행하며, `auth-service`에서 이 방식을 채택한 것은 OAuth2의 `state` 객체를 저장하기 위한 필수적인 선택이다.

---

## 6. 주요 설정 (application.yml)

`application.yml` 파일은 `auth-service`의 모든 외부 의존성 및 동작 환경을 정의한다.

* **`server.port: 8081`**: `auth-service`의 실행 포트를 8081로 지정한다.
* **`spring.datasource` / `spring.jpa`**: `AuthUserEntity`를 저장할 MySQL DB 연결 정보를 환경변수(DB_HOST 등)로부터 주입받는다.
* **`spring.data.redis`**: Refresh Token, 인증 코드, OAuth2 State 저장을 위한 Redis(`REDIS_HOST`) 연결 정보를 정의한다.
* **`spring.kafka`**:
    * **`producer`**: SAGA 시작(`UserRegistrationRequestedEvent`)을 위해 Kafka로 이벤트를 발행(serialize)하는 설정을 정의한다.
    * **`consumer`**: SAGA 응답(`UserProfileCreationSuccessEvent` 등)을 수신(deserialize)하기 위한 설정을 정의한다. `spring.json.trusted.packages`와 `type.mapping`은 `common-module`의 DTO를 올바르게 역직렬화하기 위해 필수적이다.
* **`spring.mail`**: `EmailService`가 인증 코드를 발송하기 위해 사용할 GMail SMTP 서버(`smtp.gmail.com`) 및 계정 정보를 설정한다.
* **`spring.security.oauth2`**: Google, Naver, Kakao 각 소셜 공급자로부터 발급받은 `client-id`, `client-secret` 및 `scope`를 정의한다. 이 설정은 `CustomOAuth2UserService`의 기반 데이터가 된다.
* **`jwt`**:
    * `secret`: `JwtTokenProvider`가 토큰 서명에 사용할 비밀 키를 환경변수(`JWT_SECRET`)로부터 주입받는다.
    * `access_expiration_time` / `refresh_expiration_time`: 각각 Access Token과 Refresh Token의 만료 시간을 설정한다.
* **`app.frontend.redirect-url`**: 소셜 로그인 성공 시, `OAuth2LoginSuccessHandler`가 사용자를 리디렉션시킬 프론트엔드 콜백 URL을 정의한다.

---