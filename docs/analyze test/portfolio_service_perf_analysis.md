# Portfolio Service 성능 개선 분석 보고서

## 1. 개요
`portfolio-service`는 메인 페이지 노출 빈도가 높고, 조회수(`viewCount`)와 좋아요(`likeCount`) 같은 동적 데이터의 변경이 잦은 서비스이다. 초기 테스트에서 **60%에 달하는 실패율**과 **시스템 마비(System Hang)** 현상이 발생하였으며, 이를 해결하기 위해 **인덱스 튜닝**과 **Split Caching(Write-Back)** 전략을 적용하였다.

## 2. 테스트 환경 및 시나리오
* **테스트 도구**: k6
* **스크립트**: `tests/k6/scenarios/1_isolated/portfolio_stress.js`
* **시나리오 구성**:
  * **Explorer**: 목록 및 상세 조회 (DB 조회 효율 및 캐시 검증)
  * **Liker**: 좋아요 토글 (동시성 Write 트랜잭션 검증)
* **부하 수준**: VUs 40~50명

## 3. 성능 비교 (Before vs After)

| 지표 (Metric) | 개선 전 (Before) | 개선 후 (After) | 증감률 |
| :--- | :--- | :--- | :--- |
| **성공률 (Success Rate)** | **39.33% (실패 60%)** | **99.97% (안정적)** | **▲ 60.6%p (정상화)** |
| **평균 응답 시간 (Avg Latency)** | 1.75 s (Max 35.5s) | 385 ms (Max 2.95s) | ▼ 78.0% (단축) |
| **DB 스캔량 (read_next)** | **37,000 / sec** | **79 / sec** | **▼ 99.8% (병목 제거)** |
| **Load Average** | 55.5 (폭주) | 13.3 (안정) | ▼ 76.0% (감소) |
| **DB Lock 대기** | 발생 (Write on Read) | 0 | 병목 해소 |

<p align="center">
  <img src="../../images/test\portfolio\portfolio_before_mysql_handlers.png" width="1200">
  <img src="../../images/test\portfolio\portfolio_after_mysql_handlers.png" width="1200">
</p>


> *해석: 개선 전 초당 3.7만 건에 달하던 불필요한 DB 행(Row) 스캔이 인덱스 적용 후 79건으로 획기적으로 감소함.*

## 4. 개선 전 (Before) 원인 분석: "DB 스캔 폭발과 잠금의 악순환"

초기 테스트 결과, 요청의 절반 이상이 실패하고 시스템 부하(Load Average)가 55.5까지 치솟는 심각한 장애 상태였다.

### 4.1. DB 스캔 폭발 (MySQL read_next: 37K)
* **현상**: `GET /portfolios` (목록 조회) 요청 시, `read_next` 지표가 초당 **37,000회** 발생.
* **원인**: 정렬 기준인 `created_at` 또는 `popularity_score`에 대한 적절한 인덱스가 없어, DB가 매 요청마다 **Full Table Scan**에 가까운 비효율적인 스캔과 메모리 정렬(Filesort)을 수행함.

<p align="center">
  <img src="../../images/test\portfolio\portfolio_before_describe_mysql.png" width="1200">
</p>



### 4.2. 'Write on Read'의 저주 (Lock Contention)
* **현상**: 단순 조회 요청임에도 불구하고 Load Average가 55.5까지 폭발하며 스레드들이 무한 대기 상태에 빠짐.
* **원인**: 상세 조회(`getPortfolioDetail`) 로직 내에 조회수를 증가시키는 `UPDATE` 쿼리(`increaseViewCount`)가 포함되어 있었음.
* **결과**: 50명의 사용자가 동시에 조회를 시도할 때마다 DB Row Lock(쓰기 잠금) 경합이 발생하여, 읽기 작업까지 블로킹(Blocking)되는 교착 상태 유발.

<p align="center">
  <img src="../../images/test\portfolio\portfolio_before_load_average.png" width="600">
</p>


## 5. 개선 후 (After) 분석: "구조적 개선을 통한 안정화"

DB 튜닝과 아키텍처 변경(Split Caching) 적용 후, 시스템은 모든 트래픽을 안정적으로 처리하는 상태로 전환되었다.

### 5.1. DB 튜닝: Index Range Scan 적용
* **조치**: 조회 쿼리의 `WHERE` 및 `ORDER BY` 조건에 맞춰 복합 인덱스(`idx_portfolio_...`)를 적용.
* **결과**:
  * `read_next` 수치가 37,000 → **78.9**로 급감.
  * PMM 지표 상 `Select Range` 비율이 증가하며, 쿼리가 인덱스 범위를 타고 효율적으로 데이터를 조회함이 증명됨.

### 5.2. Split Caching 도입 ('Write on Read' 제거)
* **조치**: 조회수 증가 로직을 DB 직접 업데이트에서 **Redis HyperLogLog/Increment(메모리 연산)**로 변경하고, `PortfolioBatchScheduler`를 통해 주기적으로 DB에 반영(Write-Back)하도록 구조 개선.
* **결과**:
  * **Table Locks Waited: 0**. 조회 요청 시 DB Lock이 전혀 발생하지 않음.
  * Load Average가 13.3으로 안정화되어 시스템 여유 자원 확보.
  * 조회 성능이 확보되자, `Like`(좋아요)와 같은 쓰기 작업도 300ms 내외로 쾌속 처리됨.

<p align="center">
  <img src="../../images/test\portfolio\portfolio_after_mysql_locks.png" width="800">
</p>

### 5.3. 리소스 사용 효율화
* **HikariCP**: Active Connections가 평균 3.5개(Max 12개)로 유지됨. 트랜잭션이 빠르게 종료되어 커넥션 풀 고갈 현상이 사라짐.
* **JVM Memory**: Heap 사용량이 70% 수준에서 안정적으로 유지되며, GC 또한 지연 없이 수행됨.

<p align="center">
  <img src="../../images/test\portfolio\portfolio_after_connections.png" width="1200">
</p>

## 6. 결론
`portfolio-service`의 성능 문제는 단순한 쿼리 튜닝을 넘어, **"조회 시 쓰기 잠금(Write Lock)을 유발하는 안티 패턴"**을 해소하는 것이 핵심이었다.
1.  **인덱스 튜닝**으로 Full Scan을 제거하여 DB 부하를 99.8% 감소시켰다.
2.  **Split Caching(Write-Back)** 도입으로 조회 트래픽에서 DB Lock을 완전히 제거하였다.

그 결과, 60%에 달하던 에러율을 0%로 잡고 응답 속도를 4배 이상 빠르게 개선하여 대규모 트래픽을 감당할 수 있는 아키텍처를 완성하였다.