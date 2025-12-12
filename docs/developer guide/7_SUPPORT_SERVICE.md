# 7_SUPPORT_SERVICE.md

## 1. 개요

`support-service`는 서비스 내의 **공지사항(Notice)**과 **자주 묻는 질문(FAQ)**을 관리하는 마이크로서비스이다.

데이터의 변경 빈도는 매우 낮지만 조회 빈도가 높은 특성을 고려하여, **적극적인 캐싱(Global Caching)** 정책을 적용하였다. 관리자(Admin)만이 데이터를 생성/수정할 수 있으며, 일반 사용자는 Read-Only로 접근한다.

---

## 2. 핵심 기능

* **공지사항(Notice)**: 중요 공지(`isImportant`) 상단 고정, 페이징 목록 조회, 상세 조회.
* **FAQ**: 카테고리별(결제, 계정 등) 필터링 조회.
* **관리자 기능**: Admin 권한을 가진 사용자만 CUD(Create, Update, Delete) API 접근 가능.
* **Custom Caching**: Spring Cache 추상화와 `RedisTemplate`을 혼용하여 페이지네이션 객체의 직렬화 문제 해결.

---

## 3. 상세 아키텍처 및 데이터 흐름

### 3.1. 캐싱 전략 및 직렬화 이슈 해결

Spring Data Redis 사용 시, `Page<T>` (구체적으로 `PageImpl`) 객체는 기본 생성자가 없고 복잡한 구조를 가져 JSON 직렬화/역직렬화(`Jackson`) 시 에러가 발생하기 쉽다. 이를 해결하기 위해 **Wrapper Class 패턴**을 도입했다.

1.  **Wrapper Class (`CustomPageResponse<T>`)**:
    * `PageImpl`의 내용물(content, pageNumber, totalPages 등)을 POJO 형태의 필드로 옮겨 담는 클래스.
    * 기본 생성자(`@NoArgsConstructor`)를 포함하여 Jackson이 쉽게 역직렬화할 수 있도록 설계됨.
2.  **Manual Caching Flow (`NoticeService.getNotices`)**:
    * `@Cacheable` 어노테이션 대신 `RedisTemplate`을 직접 사용한다.
    * **Cache Key**: `noticeList::{page}-{size}` (예: `noticeList::0-10`)
    * **Cache Hit**: JSON 문자열을 조회하여 `CustomPageResponse`로 역직렬화한 후, 다시 `PageImpl`로 변환하여 Controller에 반환한다.
    * **Cache Miss**: DB 조회 후 `CustomPageResponse`로 감싸서 JSON으로 직렬화하여 Redis에 저장(TTL 1시간)한다.

### 3.2. Cache Eviction (데이터 갱신)

관리자에 의해 공지사항이나 FAQ가 변경되면 데이터 일관성을 위해 캐시를 즉시 제거해야 한다.

* **`@CacheEvict` 활용**:
    * **수정/삭제 시**: 해당 ID의 상세 조회 캐시(`noticeDetail::{id}`)를 삭제한다.
    * **목록 영향**: 데이터가 추가되거나 삭제되면 페이징 순서가 바뀌므로, 목록 캐시 전체(`noticeList`, `faqs`)를 초기화(`allEntries = true`)한다.
* **트랜잭션**: `create`, `update`, `delete` 메서드는 `@Transactional` 범위 내에서 실행되며, 성공적으로 커밋된 경우에만 캐시가 삭제되도록 동작한다.

---

## 4. API 및 보안 (Security)

### 4.1. 권한 분리

`support-service`는 `Gateway` 수준이 아닌 서비스 내부의 `SecurityConfig`는 존재하지 않거나 단순화되어 있으며, 주로 비즈니스 로직이나 Gateway 라우팅 단에서 권한을 제어하는 구조를 따른다. (소스 코드 상 `SecurityConfig`에 대한 상세 구현은 생략되었으나, 컨트롤러 레벨에서 Admin 검증 로직이 수행된다.)

* **Public API**: `GET /notices`, `GET /notices/{id}`, `GET /faqs` - 인증 없이 접근 가능.
* **Admin API**: `POST/PUT/DELETE /admin/**` - 관리자 권한 토큰이 있어야 접근 가능하도록 Gateway 라우트 필터 또는 인터셉터에서 제어된다.

---

## 5. 주요 설정

### 5.1. Redis Key 규칙
* **공지사항 목록**: `noticeList::{page}-{size}` (TTL 1h)
* **공지사항 상세**: `noticeDetail::{id}` (TTL 영구 혹은 1h)
* **FAQ 전체**: `faqs::all`
* **FAQ 카테고리**: `faqs::{category}`

---