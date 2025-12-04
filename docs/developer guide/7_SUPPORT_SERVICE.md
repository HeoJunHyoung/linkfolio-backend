# 7_SUPPORT_SERVICE.md

## 1. 개요

`support-service`는 공지사항(Notice)과 자주 묻는 질문(FAQ)을 제공하는 서비스이다.

데이터의 특성상 **작성(Write) 빈도는 극히 낮고, 조회(Read) 빈도는 매우 높다.** 따라서 DB 부하를 최소화하고 응답 속도를 극대화하기 위해, 거의 모든 조회 API에 **Spring Cache (Redis)**가 적용되어 있다.

---

## 2. 핵심 기능

* **공지사항 (Notice)**:
    * 관리자(ADMIN)만 작성/수정/삭제 가능.
    * 상단 고정(`isImportant`) 기능을 통해 중요 공지를 리스트 상단에 노출.
    * 목록 조회와 상세 조회가 분리되어 있으며, 각각 독립적인 캐싱 정책 적용.
* **자주 묻는 질문 (FAQ)**:
    * 카테고리별(회원, 결제, 포트폴리오 등) 필터링 조회.
    * 데이터 크기가 작아 목록 조회 시 전체 내용을 반환(상세 조회 불필요).

---

## 3. 캐싱 구현 전략 (Caching Implementation)

Redis 캐싱을 효율적으로 적용하기 위해 다음과 같은 구현 패턴을 사용한다.

### 3.1. Page 객체 래핑 (Data Transfer Strategy)

Spring Data JPA의 `PageImpl` 객체는 기본 생성자가 없어 Jackson 라이브러리를 통한 JSON 역직렬화(Deserialization) 시 문제가 발생한다. 이를 해결하기 위해 **Custom Wrapper Pattern**을 적용했다.

* **`CustomPageResponse<T>`**:
    * `Page` 객체의 데이터(`content`, `page`, `size`, `totalElements`)를 담는 POJO 클래스.
    * `@NoArgsConstructor`를 포함하여 Redis 입출력 시 직렬화 호환성을 보장한다.
* **Flow**:
    * `DB 조회` -> `Page<T>` -> `CustomPageResponse<T> (Wrapping)` -> `Redis 저장 (JSON)`
    * `Redis 조회` -> `CustomPageResponse<T>` -> `.toPage()` -> `Page<T> (Restoration)` -> `Controller 반환`

### 3.2. 캐시 무효화 (Eviction) 전략

데이터의 일관성을 유지하기 위해 관리자에 의한 변경(CUD) 발생 시 관련 캐시를 즉시 제거한다.

* **공지사항 수정/삭제 시**:
    * `@CacheEvict(value = "noticeDetail", key = "#id")`: 해당 글의 상세 캐시 삭제.
    * `@CacheEvict(value = "noticeList", allEntries = true)`: **모든 목록 캐시 삭제**.
        * *이유*: 제목이나 상단 고정 여부가 변경되면, 몇 페이지에 해당 글이 있는지 추적하기 어렵기 때문에 전체 목록 캐시를 초기화하여 정합성을 맞춘다.