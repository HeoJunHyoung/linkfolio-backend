# Community Service 성능 개선 분석 보고서

## 1. 개요
`community-service`는 게시글 목록 조회, 상세 조회, 댓글 작성 및 팀원 모집 기능을 제공하는 서비스이다.
초기 테스트에서 시스템이 마비되는 수준의 장애가 발생하였으며, 분석 결과 단순한 리소스 부족이 아닌 **DB 쿼리의 비효율성(Full Table Scan)**이 핵심 원인임이 밝혀졌다. 본 보고서는 인덱스 튜닝과 캐싱을 통해 해당 병목을 해소한 과정을 기술한다.

## 2. 테스트 환경 및 시나리오
* **테스트 도구**: k6
* **스크립트**: `tests/k6/scenarios/1_isolated/community_stress.js`
* **시나리오 구성**:
  * **Viewer (50 VUs)**: 게시글 목록(`GET /posts`) 및 상세 조회
  * **Writer (10 VUs)**: 댓글 작성 (Write)
* **총 부하 수준**: 60 VUs

## 3. 성능 비교 (Before vs After)

| 지표 (Metric) | 개선 전 (Before) | 개선 후 (After) | 증감률 |
| :--- | :--- | :--- | :--- |
| **성공률 (Success Rate)** | 28.88% (대부분 실패) | **100.00% (전체 성공)** | **▲ 71.12%p (정상화)** |
| **오류율 (Error Rate)** | 71.07% (서버 다운) | **0.00%** | **완벽 개선** |
| **평균 응답 시간 (Avg Latency)** | 4.03s (Max 17.7s) | **656ms** (Max 3.04s) | **▼ 83% (단축)** |
| **DB 스캔량 (read_rnd_next)** | **105,000 / sec** | **156 / sec** | **▼ 99.8% (병목 제거)** |
| **Load Average** | 46.0 (Critical) | 11.8 (Warning) | ▼ 74% (감소) |
| **커넥션 대기 (Pending)** | 9 (고갈) | 0 (여유) | 병목 해소 |

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\community\community_after_response_time.png" width="1200">
</p>

> *해석: 개선 전에는 대다수 요청이 연결 거부(Connection Refused)되거나 타임아웃되었으나, 튜닝 후 모든 요청을 안정적으로 처리함.*

## 4. 개선 전 (Before) 원인 분석: "비효율적 쿼리로 인한 시스템 마비"

초기 테스트(60 VUs) 결과, 서버가 응답을 거부(`connectex: actively refused`)하며 시스템 장애가 발생했다. 커넥션 풀이 설정되어 있음에도 불구하고 대기열(Pending)이 발생하고 CPU 부하(Load Average)가 폭발했다.

### 4.1. DB 스캔 부하 폭발 (MySQL read_rnd_next: 105K) - [핵심 원인]
* **현상**: `MySQL Handlers` 지표 중 `read_rnd_next`가 초당 **105,000회** 발생.
* **분석**: 게시글 목록 조회(`GET /posts`) 시 정렬(`ORDER BY`)과 필터링(`WHERE`)을 위한 인덱스가 전혀 적용되지 않음. 요청 1건당 DB가 10만 개의 행을 디스크에서 읽어들이는 **Full Table Scan**이 발생함.
* **Filesort 발생**: 메모리상에서 데이터를 강제로 재정렬(`Sort Rows: 53.7`)하느라 DB 리소스가 고갈됨.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\community\community_before_mysql_handlers.png" width="1200">
</p>

### 4.2. VUs 조절 테스트 결과 (VUs 60 → 30)
트래픽 과다 여부를 확인하기 위해 사용자 수를 절반으로 줄여 재테스트하였으나 근본적인 문제는 해결되지 않았다.
* **HikariCP**: Pending Connections는 0으로 해소됨 (요청량 감소 효과).
* **DB 부하**: `read_rnd_next`는 여전히 **95,000/s** 수준 유지.
* **결론**: 이는 트래픽 양(Capacity)의 문제가 아니라, **쿼리 효율성(Efficiency)**의 문제임이 증명됨. 소수의 요청이라도 테이블 전체를 스캔하는 비효율이 시스템을 잠식함.

### 4.3. 서버 리소스 포화
* **Load Average**: Max 46.0 (1 Core 기준 적정치 1.0의 46배).
* DB가 응답하지 않아 애플리케이션 스레드가 블로킹(Blocked)되었고, 이로 인해 후속 요청들이 연결조차 맺지 못하고 거절당함.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\community\community_before_load_average.png" width="800">
</p>

## 5. 개선 후 (After) 분석: "인덱스 튜닝을 통한 병목 해소"

적절한 복합 인덱스(`idx_post_qna_date` 등) 적용 및 캐싱 도입 후, 시스템은 안정적인 상태로 전환되었다.

### 5.1. DB 튜닝: Index Range Scan 적용
* **조치**: 조회 패턴에 맞춘 복합 인덱스 생성.
* **결과**:
  * `read_rnd_next`(무작위 스캔) 수치가 105,000 → **156**으로 획기적으로 감소.
  * 대신 `read_next`(인덱스 스캔) 지표가 활성화되며, DB가 필요한 데이터만 효율적으로 조회하기 시작함.


<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\community\community_after_mysql_handlers.png" width="1200">
</p>

### 5.2. HikariCP 커넥션 풀 안정화
* **지표**: Active Connections 평균 6.7개, **Pending 0개**.
* **분석**: 쿼리 실행 속도가 빨라지면서 트랜잭션 점유 시간이 단축됨. 설정된 30개의 커넥션 풀 내에서 60 VUs의 트래픽을 여유롭게 처리함.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\community\community_after_connections.png" width="1200">
</p>


### 5.3. 서버 부하 감소
* **Load Average**: 46.0 → **11.8**.
* **한계점**: 여전히 1 Core CPU 기준으로는 부하가 높은 편(Warning 수준)이며, 응답 속도(P95 1.55s)도 최적화 여지가 있음. 이는 DB 병목은 해소되었으나 애플리케이션 레벨의 로직 처리나 GC, 또는 CPU 사양의 물리적 한계로 추정됨.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\community\community_after_load_average.png" width="800">
</p>

## 6. 결론
`community-service`의 장애 원인은 **DB Full Table Scan**이었다. 인덱스가 없는 상태에서는 커넥션을 늘리거나 트래픽을 줄여도 근본적인 해결이 불가능했다.
**인덱스 튜닝**을 통해 스캔 효율을 99.8% 개선한 결과, 오류율 0%의 안정적인 서비스를 확보할 수 있었다.