# 5_COMMUNITY_SERVICE.md

## 1. 개요

`community-service`는 LinkFolio 내의 소통 공간(QnA, 정보공유, 팀원모집)을 담당한다.

게시글과 계층형 댓글(대댓글) 구조를 지원하며, **팀원 모집** 기능의 경우 `chat-service`와 연동하여 1:1 채팅을 통한 지원 시스템을 구축하였다. 포트폴리오 서비스와 마찬가지로 대용량 트래픽에 대비한 **Redis Caching** 및 **CDC 기반 프로필 동기화**가 적용되어 있다.

---

## 2. 핵심 기능

* **게시판 관리**: 카테고리(QNA, INFO, RECRUIT)별 게시글 CRUD 및 페이징 조회
* **계층형 댓글**: 부모-자식 관계를 가진 무한 Depth(설정 가능) 대댓글 구조 지원
* **QnA 특화**: 질문 해결 여부(`isSolved`), 답변 채택 기능
* **팀원 모집 연동**: 모집글에서 즉시 1:1 채팅으로 지원하기 (`feign-client` -> `chat-service`)
* **북마크 및 조회수**: Redis를 활용한 동시성 제어 및 배치 업데이트

---

## 3. 상세 아키텍처 및 데이터 흐름

### 3.1. 계층형 댓글 구조 (Hierarchical Comments)

DB에는 `parent_id`를 통한 **인접 목록(Adjacency List)** 형태로 평탄화(Flat)되어 저장된다. 하지만 클라이언트에는 트리(Tree) 구조로 반환해야 한다.

1.  **Fetch**: `findCommentsByPostId`를 통해 해당 게시글의 모든 댓글을 List로 조회한다.
2.  **Processing**: `PostService.convertToCommentHierarchy` 메서드에서 메모리 로직을 수행한다.
    * `Map<Long, DTO>`을 생성하여 O(1) 접근이 가능하게 한다.
    * 리스트를 순회하며 부모가 있으면 부모 DTO의 `children` 리스트에 자신을 추가하고, 부모가 없으면 Root 리스트에 추가한다.
3.  **Return**: 최종적으로 Root 리스트만 반환하여 JSON 트리 구조를 완성한다.

### 3.2. 팀원 모집 및 채팅 연동 (Recruitment Flow)

사용자가 '팀원 모집' 게시글을 보고 [지원하기] 버튼을 누르면 다음과 같은 흐름이 발생한다.

1.  **API Call**: 클라이언트 -> `community-service` (`/posts/{id}/recruit/apply`)
2.  **Validation**: 게시글 카테고리가 `RECRUIT`인지, 상태가 `OPEN`인지 검증한다.
3.  **Feign Client**: `ChatServiceClient`를 통해 `chat-service`의 내부 API(`/internal/chat/send`)를 호출한다.
4.  **Message delivery**: 별도의 채팅방 생성 절차 없이, 지원자와 작성자 간의 1:1 메시지가 전송된다. (채팅 서비스가 메시지 수신 시 자동으로 방을 생성하거나 기존 방을 사용함)

---

## 4. 핵심 로직 상세

### 4.1. 게시글 상세 조회 및 캐싱 (`PostService.getPostDetail`)

`portfolio-service`와 유사한 **Split Caching** 전략을 사용하지만, 댓글 구조의 복잡성으로 인해 일부 차이가 있다.

* **Caching Scope**: 게시글 본문(작성자 정보 포함)은 `post:info:{id}` 키로 캐싱된다. 댓글 목록은 빈번한 생성/삭제로 인해 캐싱 효율이 낮아 DB에서 실시간 조회한다.
* **Dynamic Stats**: 조회수, 북마크 수, 댓글 수는 `post:stats:{id}` Redis Hash에 저장되어 실시간으로 갱신된다.
* **Response Assembly**:
    1.  Redis에서 `post:info` 조회 (Miss 시 DB 조회 및 캐싱).
    2.  DB에서 댓글 목록 조회 및 계층 구조 변환.
    3.  Redis에서 통계 데이터(`stats`) 조회 및 병합.
    4.  DB에서 현재 사용자의 북마크 여부 확인(`existsBy...`).

### 4.2. QnA 답변 채택

* **검증**: 게시글 작성자만이 답변을 채택할 수 있다.
* **처리**:
    * 해당 댓글(`PostCommentEntity`) 상태를 채택됨으로 변경.
    * 게시글(`PostEntity`) 상태를 `isSolved = true`로 변경.
    * **Cache Eviction**: 게시글의 해결 상태가 변경되었으므로 `post:info:{id}` 캐시를 삭제한다.

---

## 5. 주요 데이터 모델

### 5.1. `PostEntity`
* **`category`**: `ENUM(QNA, INFO, RECRUIT)` - 게시판 성격을 구분.
* **`recruitmentStatus`**: 모집글일 경우 `OPEN/CLOSED` 상태 관리.
* **User Data**: `PostUserProfileEntity`와 연관관계를 맺지 않고, 필요한 필드(작성자 ID 등)만 관리하거나 별도 저장소(`post_user_profile` 테이블)를 통해 CDC로 동기화된 데이터를 참조한다.

### 5.2. `PostUserProfileEntity`
* `user-service`의 변경사항을 수신하는 전용 테이블. 게시글 작성자의 닉네임 변경 등이 발생하면, 이 테이블이 업데이트되어 조인 조회 시 최신 정보를 반영한다.

---