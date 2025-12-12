## 1. 개요

`common-module`은 LinkFolio MSA 프로젝트의 **공통 라이브러리 모듈**이다.

모든 마이크로서비스(예: `user-service`, `auth-service`, `portfolio-service`)가 공유하는 DTO, Enum, 공통 예외 처리 로직, 엔티티 기반 클래스 등을 중앙에서 관리하여 코드 중복을 제거하고 일관성을 유지하는 것을 목적으로 한다.

이 모듈은 실행 가능한 Spring Boot 애플리케이션이 아닌, 다른 서비스들에 의해 의존성으로 포함되는 **JAR 라이브러리**로 패키징된다.

---

## 2. 핵심 구성 요소

### 2.1. 공통 예외 처리 (`exception`)

모든 서비스에서 일관된 예외 응답 형식을 보장하기 위한 핵심 로직을 제공한다.

* **`GlobalExceptionHandler`**: `@RestControllerAdvice`를 통해 모든 컨트롤러에서 발생하는 예외를 가로챈다.
* **`BusinessException`**: 서비스 전반에서 사용되는 공통 런타임 예외. 각 서비스의 `ErrorCode`를 담아 `GlobalExceptionHandler`로 전달한다.
* **`ErrorResponse`**: `timestamp`, `status`, `code`, `message` 필드를 갖는 표준화된 JSON 오류 응답 DTO이다.
* **`ErrorCode` (Interface)**: 각 서비스의 `ErrorCode` Enum(예: `auth-service`의 `ErrorCode`)이 반드시 구현해야 하는 공통 인터페이스이다.

### 2.2. 내부 인증 필터 (`filter`)

* **`InternalHeaderAuthenticationFilter`**: `apigateway-service`를 통과한 요청을 처리하는 내부 서비스(예: `user-service`, `portfolio-service`)의 `SecurityConfig`에 등록된다.
* **동작**: 게이트웨이가 주입한 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 읽어 신뢰할 수 있는 `AuthUser` 객체를 생성한다.
* **역할**: 생성된 인증 객체를 `SecurityContextHolder`에 등록하여, 내부 서비스들이 JWT 검증 없이도 `@AuthenticationPrincipal` 어노테이션 등으로 사용자를 즉시 식별할 수 있게 한다.

### 2.3. 공통 엔티티 및 Enum (`entity`)

* **`BaseEntity`**: 모든 JPA 엔티티가 상속받는 `@MappedSuperclass`이다. `@CreatedDate` (`createdAt`)와 `@LastModifiedDate` (`lastModifiedAt`) 필드를 제공하여 생성/수정 시간을 자동으로 관리한다.
* **`enumerate`**:
  * `Role.java` (USER, ADMIN)
  * `Gender.java` (MALE, FEMALE)
  * `UserProvider.java` (LOCAL, GOOGLE, NAVER, KAKAO)

### 2.4. 이벤트 DTO (`dto.event`)

Kafka를 통해 서비스 간 비동기 통신(SAGA 패턴, 데이터 동기화)에 사용되는 공통 이벤트 DTO를 정의한다.

* **`UserRegistrationRequestedEvent`**: 회원가입 SAGA 시작 이벤트 (Auth -> User).
* **`UserProfileCreationSuccessEvent`**: SAGA 성공 이벤트 (User -> Auth).
* **`UserProfileCreationFailureEvent`**: SAGA 보상 트랜잭션 이벤트 (User -> Auth).
* **`UserProfilePublishedEvent`**: 프로필 생성/변경 전파(Fan-out) 이벤트 (User -> Auth, Portfolio 등).

### 2.5. 보안 및 인증 객체 (`dto.security`)

#### AuthUser 구분 (vs Auth Service)

본 프로젝트에는 `AuthUser`라는 이름을 가진 클래스가 두 곳(`common-module`, `auth-service`)에 존재한다. 두 클래스는 역할과 목적이 완전히 다르므로 혼동하지 않도록 주의해야 한다.

**1. 비교 및 차이점**

| 구분 | **Common Module (본 모듈)** | **Auth Service** |
| :--- | :--- | :--- |
| **패키지** | `com.example.commonmodule.dto.security.AuthUser` | `com.example.authservice.dto.AuthUser` |
| **핵심 역할** | **인증 결과물 (Context / 방문증)** | **인증 수행자 (Principal / 신분증)** |
| **구현체** | 단순 POJO (DTO) | `UserDetails`, `OAuth2User` 구현체 |
| **포함 정보** | `userId`, `email`, `role` (최소 정보) | `password`, `attributes` 등 민감 정보 포함 |
| **사용 시점** | 로그인 완료 **후** (API 요청 처리 시) | 로그인/토큰 발급 **과정 중** |

**2. 분리 이유**

1.  **의존성 최적화**: 다른 마이크로서비스(`portfolio`, `chat` 등)가 Spring Security의 무거운 의존성 없이도 사용자 정보를 처리할 수 있도록 경량화하였다.
2.  **보안 강화**: 비즈니스 로직을 수행하는 서비스들 사이에서 패스워드 등 민감한 정보가 객체에 담겨 돌아다니는 것을 원천적으로 차단한다.

> **Note**: 비즈니스 로직(Service 레이어) 개발 시에는 항상 `common-module`의 `AuthUser`를 사용한다.

---

## 3. 의존성 관리 (`pom.xml`)

`common-module`의 `pom.xml`은 대부분의 서비스가 공통으로 필요로 하는 핵심 의존성들을 포함한다.

* `spring-boot-starter-data-jpa`
* `spring-boot-starter-web` (Spring MVC)
* `spring-boot-starter-security`

> 의존성 제외(Exclusion) 전략
>
> `common-module`은 Spring MVC(`web`)와 JPA(`data-jpa`) 기반으로 작성되었다.
>
> 하지만 `apigateway-service`와 같이 `WebFlux(Reactive)`를 사용하거나 `DB가 필요 없는` 서비스는, `common-module`을 의존할 때 반드시 이 의존성들을 `<exclusions>` 태그로 제외해야 한다.
>
> *`apigateway-service/pom.xml` 예시:*
> ```xml
> <dependency>
>     <groupId>com.example</groupId>
>     <artifactId>common-module</artifactId>
>     <version>${project.version}</version>
>     <exclusions>
>         <exclusion>
>             <groupId>org.springframework.boot</groupId>
>             <artifactId>spring-boot-starter-web</artifactId>
>         </exclusion>
>         <exclusion>
>             <groupId>org.springframework.boot</groupId>
>             <artifactId>spring-boot-starter-data-jpa</artifactId>
>         </exclusion>
>         <exclusion>
>             <groupId>org.springframework.boot</groupId>
>             <artifactId>spring-boot-starter-security</artifactId>
>         </exclusion>
>     </exclusions>
> </dependency>
> ```