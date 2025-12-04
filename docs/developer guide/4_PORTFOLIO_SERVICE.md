# 4_PORTFOLIO_SERVICE.md

## 1. 개요

`portfolio-service`는 LinkFolio의 핵심 도메인인 **사용자 포트폴리오**를 전담 관리하는 마이크로서비스이다.

서비스 특성상 쓰기(Write)보다 읽기(Read) 트래픽이 압도적으로 높으며, 특히 메인 페이지의 카드 목록 조회와 상세 페이지 조회는 시스템 전체 부하의 큰 비중을 차지한다. 따라서 이 서비스는 단순한 CRUD를 넘어 **Redis를 활용한 고도화된 캐싱 전략(Split & Merge)**과 **쓰기 지연(Write-Back) 패턴**이 집약되어 있다.

---

## 2. 핵심 기능

* **1인 1포트폴리오 정책**: 사용자 ID(`userId`)를 기준으로 유니크한 포트폴리오를 관리한다.
* **CQRS 성격의 조회 분리**:
    * **목록 조회**: `QueryDSL`과 `Slice`를 이용한 무한 스크롤 최적화.
    * **상세 조회**: 정적/동적 데이터 분리 캐싱을 통한 성능 최적화.
* **데이터 동기화 (Event Consumer)**: `user-service`의 프로필 변경 이벤트를 수신하여 로컬 DB(`portfolio`)를 동기화, 조인(Join) 없는 조회 환경을 보장한다.
* **배치(Batch) 기반 통계 반영**: 조회수 및 좋아요 수를 Redis에 선반영 후, 스케줄러를 통해 DB에 일괄 저장한다.

---

## 3. 상세 조회 및 캐싱 전략 (Split & Merge)

포트폴리오 상세 조회(`getPortfolioDetails`)는 **Split & Merge(분리 및 병합)** 전략을 사용한다. 이는 조회수 증가와 같은 빈번한 메타데이터 변경이 전체 콘텐츠 캐시를 오염(Invalidate)시키는 것을 방지하기 위함이다.

### 3.1. 데이터 분리 (Key Strategy)

데이터의 변경 주기에 따라 Redis Key를 두 그룹으로 분리하여 관리한다.

1.  **정적 데이터 (Static Data)**
    * **내용**: 제목, 자기소개, 기술 스택, 프로젝트 목록 등.
    * **Key**: `portfolio:details:{portfolioId}`
    * **특징**: 변경 빈도가 낮음. 1시간의 긴 TTL(Time-To-Live)을 가지며, 사용자가 명시적으로 수정을 요청했을 때만 캐시를 삭제(Evict)한다.
    * **로직**: `Cache Aside` 패턴. Redis에 없으면 DB에서 조회하여 적재한다.
2.  **동적 데이터 (Dynamic Data)**
    * **내용**: 조회수(`viewCount`), 좋아요 수(`likeCount`).
    * **Key**: `portfolio:stats:{portfolioId}` (Hash 구조)
    * **특징**: 실시간으로 변함. DB를 거치지 않고 Redis 메모리 상에서 원자적 연산(`HINCRBY`)으로 즉시 반영된다.

### 3.2. 조회 흐름 (Flow)

1.  클라이언트가 상세 조회를 요청한다.
2.  **[Async]** `portfolio:stats:{id}`의 `viewCount`를 `1` 증가시킨다. (Display용)
3.  **[Async]** `portfolio:views` (Batch용 Key)의 카운트도 `1` 증가시킨다.
4.  `portfolio:details:{id}`에서 정적 데이터를 조회한다. (Cache Miss 시 DB 조회)
5.  `portfolio:stats:{id}`에서 최신 통계 데이터를 조회한다.
6.  애플리케이션 메모리에서 두 데이터를 **병합(Merge)**하여 `PortfolioDetailsResponse`를 반환한다.
7.  로그인한 사용자의 경우, DB의 `portfolio_like` 테이블을 조회하여 `isLiked` 여부를 추가 매핑한다.

---

## 4. 쓰기 지연 (Write-Back) 배치

Redis에서 증가된 조회수와 좋아요 수는 실시간으로 DB에 `UPDATE` 쿼리를 날리지 않는다. 이는 DB 커넥션 풀의 고갈을 막기 위한 필수적인 전략이다.

### 4.1. 동작 방식 (`PortfolioBatchScheduler`)

1.  **적재**: 조회/좋아요 발생 시 `portfolio:views`, `portfolio:likes:delta`라는 별도의 Redis Hash Key에 변경분(Delta)을 누적한다.
2.  **실행**: 스케줄러가 **5분 주기**로 실행된다.
3.  **일괄 처리**:
    * Redis에서 변경된 ID 목록과 값을 모두 가져온다(`HGETALL`).
    * ID 목록을 순회하며 DB에 `UPDATE portfolio SET view_count = view_count + :count WHERE id = :id` 쿼리를 실행한다.
    * *최적화 포인트: JDBC Batch Update를 사용하여 네트워크 왕복 비용을 최소화한다.*
4.  **정리**: DB 반영이 완료된 Redis Key를 삭제한다.

---

## 5. 데이터 일관성 (Event Driven)

`portfolio-service`는 `user-service`에 대한 의존성을 제거하기 위해 데이터를 복제(Replication)하여 들고 있다.

* **User 변경 감지**: `user-service`에서 닉네임 변경 등이 발생하면 `UserProfilePublishedEvent`가 Kafka로 발행된다.
* **Local Update**: `PortfolioEventHandler`가 이를 수신하여, `PortfolioEntity`에 저장된 사용자 이름(`userName`) 등을 업데이트한다.
* **이점**: 포트폴리오 목록/상세 조회 시 `user-service`로 Feign 요청을 보내거나 DB 조인을 할 필요가 없어 조회 성능이 비약적으로 향상된다.