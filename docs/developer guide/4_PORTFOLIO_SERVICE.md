# 4_PORTFOLIO_SERVICE.md

## 1. 개요

`portfolio-service`는 사용자의 이력 및 포트폴리오를 관리하는 서비스이다.

단순한 CRUD 기능을 넘어, **조회 성능 최적화**와 **데이터 일관성**을 위해 고도화된 캐싱 전략을 사용한다. 특히 포트폴리오의 '정적 데이터(내용)'와 '동적 데이터(조회수, 좋아요)'를 분리하여 관리하는 **Split Caching Strategy**를 도입하였으며, `user-service`의 프로필 변경 사항을 **Kafka CDC**를 통해 실시간으로 동기화하여 조인 쿼리 없이 단일 조회만으로 데이터를 완성한다.

---

## 2. 핵심 기능

* **포트폴리오 관리**: 생성, 수정, 삭제 및 공개 여부 설정
* **Split Caching Strategy**: 정적 데이터(본문)와 동적 데이터(통계)의 라이프사이클 분리 관리
* **인기 점수 알고리즘**: Hacker News 알고리즘을 변형한 시간 감쇠 기반 인기 순위 산정
* **User Profile Sync**: Kafka CDC를 이용한 사용자 프로필(이름, 이메일 등) 비정규화 및 실시간 동기화
* **Batch Processing**: 조회수, 좋아요 수 등의 DB Write-Back을 위한 배치 처리 지원

---

## 3. 상세 아키텍처 및 데이터 흐름

### 3.1. 캐싱 전략 (Split Caching Strategy)

포트폴리오 상세 조회(`getPortfolioDetails`)는 서비스 내에서 가장 트래픽이 많은 구간이다. 이를 최적화하기 위해 데이터의 성격에 따라 캐싱 방식을 이원화하였다.

1.  **정적 데이터 (Static Data)**
    * **대상**: 제목, 내용, 기술 스택, 작성자 정보 등 변경 빈도가 낮은 데이터.
    * **저장소**: Redis (`portfolio:details:{id}`)
    * **전략**: Cache-Aside 패턴. TTL을 1시간으로 설정하며, 작성자가 내용을 수정할 때만 `Eviction`(삭제)하여 데이터 정합성을 맞춘다.
    * **이점**: 무거운 `TEXT` 데이터의 DB 조회를 획기적으로 줄인다.

2.  **동적 데이터 (Dynamic Stats)**
    * **대상**: 조회수(`viewCount`), 좋아요 수(`likeCount`) 등 실시간으로 변하는 데이터.
    * **저장소**: Redis Hash (`portfolio:stats:{id}`)
    * **전략**: Write-Back 패턴. 조회 발생 시 DB가 아닌 Redis의 값을 `INCR` 연산으로 즉시 증가시킨다. 변경된 수치는 별도의 배치(`PortfolioBatchScheduler`)가 주기적으로 DB에 반영한다.
    * **이점**: 조회수 증가 로직에서 DB Lock 발생을 원천 차단하여 성능 저하를 막는다.

### 3.2. 사용자 데이터 동기화 (Kafka CDC)

포트폴리오 목록 조회 시 작성자의 이름, 이메일 등을 보여주어야 한다. 이를 위해 매번 `user-service`를 호출(Feign)하거나 DB 조인을 하는 대신, **비정규화(Denormalization)**를 선택했다.

1.  `PortfolioEntity`는 `userId` 외에 `name`, `email`, `photoUrl` 등을 컬럼으로 직접 가진다.
2.  `user-service`에서 프로필이 변경되면 `user_db.user_profile` 토픽으로 CDC 이벤트가 발행된다.
3.  `PortfolioEventHandler`가 이 이벤트를 수신(Consume)하여 해당 `userId`를 가진 포트폴리오의 정보를 즉시 업데이트한다(`updateCache`).
4.  이를 통해 **"자가 치유(Self-Healing)"** 가능한 데이터 구조를 갖추며, 타 서비스 장애 시에도 조회가 가능하다.

---

## 4. 핵심 로직 상세

### 4.1. 인기 점수 산정 (Popularity Score)

메인 페이지의 '인기 포트폴리오' 정렬을 위해 자체 알고리즘을 사용한다. 단순히 조회수가 높은 글이 아니라, **"최신 트렌드"**를 반영하기 위해 Hacker News의 알고리즘을 변형하여 적용하였다.

* **공식**: `Score = (View * 1 + Like * 50) / (Time + 2)^1.5`
* **동작 원리**: 시간이 지날수록 분모(Time)가 기하급수적으로 커져 점수가 낮아진다. 따라서 오래된 게시글은 자연스럽게 상위권에서 내려오고, 새로운 인기글이 올라갈 기회를 얻는다.
* **반영 시점**: 사용자가 포트폴리오를 수정하거나(`updateUserInput`), 배치 작업이 돌 때 점수를 재계산(`calculateAndSetPopularityScore`)하여 `popularity_score` 컬럼에 저장한다.

### 4.2. 조회 프로세스 (`PortfolioService.getPortfolioDetails`)

1.  **Stats Increment**: Redis의 `portfolio:stats:{id}`에서 `viewCount`를 `HINCRBY`로 1 증가시킨다. 동시에 배치용 Key(`portfolio:views`)에도 기록한다.
2.  **Static Data Load**: Redis에서 본문 데이터를 조회한다. 없으면 DB에서 조회 후 캐싱한다.
3.  **Merge**: 정적 데이터(DTO)에 Redis에서 가져온 최신 `viewCount`, `likeCount`를 덮어씌운다.
4.  **Personalization**: 로그인한 사용자라면 `PortfolioLikeRepository`를 통해 '북마크 여부'를 확인하여 `isLiked` 필드를 세팅한다.

---

## 5. 주요 데이터 모델

### 5.1. `PortfolioEntity`
* **Index 전략**:
    * `idx_portfolio_popularity`: `is_published` + `popularity_score DESC` (인기순 정렬 최적화)
    * `idx_portfolio_position_publish_date`: `position` 필터링 + `last_modified_at` 정렬 (커버링 인덱스)
* **비정규화 필드**: `name`, `email` 등은 `user-service`와 데이터가 중복되지만 조회 성능을 위해 포함됨.

---