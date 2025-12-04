# Support Service 성능 개선 분석 보고서

## 1. 개요
`support-service`는 공지사항(`Notice`)과 자주 묻는 질문(`FAQ`)을 제공하는 서비스로, **변경 빈도는 낮고 조회 빈도는 매우 높은(Read-Heavy)** 특성을 가진다. 초기 테스트에서 **단순 조회임에도 불구하고 평균 2.5초가 소요**되는 성능 저하가 확인되었으며, 이를 해결하기 위해 **Global Caching 전략**과 **인덱스 최적화**를 적용하였다.

## 2. 테스트 환경 및 시나리오
* **테스트 도구**: k6
* **스크립트**: `tests/k6/scenarios/1_isolated/support_stress.js`
* **시나리오 구성**:
    * **NoticeList**: 공지사항 목록 페이징 조회 (대용량 Read 부하)
* **부하 수준**: VUs 80 (단순 조회의 극한 테스트)

## 3. 성능 비교 (Before vs After)

| 지표 (Metric) | 개선 전 (Before) | 개선 후 (After) | 증감률 |
| :--- | :--- | :--- | :--- |
| **평균 응답 시간 (Avg Latency)** | 2.57 s | **813 ms** | **▼ 68% (3배 단축)** |
| **95% 응답 시간 (P95)** | 4.54 s | 1.71 s | ▼ 62% (개선) |
| **처리량 (Throughput)** | 12.6 RPS | **24.8 RPS** | **▲ 97% (2배 증가)** |
| **DB 스캔 (Row Scan)** | 17,000 / sec | 151 / sec | ▼ 99.1% (효율화) |
| **DB 커넥션 대기 (Pending)** | 25 (병목) | 0.38 (해소) | 병목 제거 |


## 4. 개선 전 (Before) 원인 분석: "커넥션은 늘렸으나, 쿼리가 느리다"

HikariCP Pool Size를 30개로 증설한 상태에서 테스트를 진행했음에도 불구하고, 평균 응답 시간은 2.57초로 여전히 느렸다.

### 4.1. HikariCP 병목 지속 (Pending: 25)
* **현상**: Pool Size를 30으로 늘렸으나 `Active Connection`은 8~10개 수준인 반면, `Pending` 요청은 25개까지 쌓임.
* **원인**: 커넥션 개수 문제가 아님. 쿼리 처리 시간(Processing Time) 자체가 너무 길어서, 커넥션을 점유하고 있는 시간이 길어짐에 따라 회전율이 떨어진 것임.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\support\support_before_connections.png" width="1200">
</p>

### 4.2. DB 비효율성 (Sort Rows & read_rnd_next)
* **현상**: `read_rnd_next` 지표가 초당 **17,000회** 발생, `Sort Rows` 지속 발생.
* **원인**: 공지사항 목록 조회 시 정렬 기준(`isImportant`, `createdAt`)에 맞는 인덱스가 없어, 매 요청마다 **Full Table Scan**과 **Memory Filesort**가 발생함. 80명의 요청이 동시에 들어오면서 DB CPU와 I/O 부하를 가중시킴.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\support\support_before_sorts.png" width="800">
</p>

## 5. 개선 후 (After) 분석: "소프트웨어 튜닝의 완성"

인덱스 적용과 Redis 캐싱(`NoticeService.java`) 도입 후, 시스템 성능이 비약적으로 향상되었다.

### 5.1. DB 튜닝: 스캔량 99% 감소
* **Before**: 요청당 17,000건의 행을 스캔(Full Scan).
* **After**: 평균 **151건** 스캔으로 감소.
* **해석**: 인덱스(`idx_notice_important_date`)가 정확히 적용되어 DB가 필요한 데이터만 효율적으로 조회하게 됨. DB 리소스 여유가 생기며 처리량이 2배(12.6 → 24.8 RPS)로 증가함.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\support\support_after_mysql_handlers.png" width="1200">
</p>

### 5.2. HikariCP 병목 해소
* **결과**: `Pending Connections`가 25 → **0.38**로 사실상 소멸됨.
* **해석**: 쿼리 수행 속도가 813ms로 빨라지면서 커넥션 반납과 재사용이 원활해짐. 스레드 대기 현상이 사라짐.

<p align="center">
  <img src="C:\- linkfolio\linkfolio-backend\images\test\support\support_after_connections.png" width="1200">
</p>

### 5.3. CPU Load Average (36) 분석
* **현상**: 성능은 좋아졌으나 Load Average가 36으로 여전히 높음.
* **원인**: 테스트 환경이 **1 Core CPU**이기 때문. I/O Wait(DB 대기)은 해소되었으나, 80 VUs의 요청을 1개의 코어가 처리하며 잦은 **Context Switching**이 발생함.
* **결론**: 현재 상태는 소프트웨어적 튜닝(인덱스, 캐시)으로는 한계치까지 성능을 끌어올린 상태이며, 추가적인 성능 향상을 위해서는 CPU 코어 증설(Scale-up)이 필요함.

## 6. 결론
`support-service`의 성능 문제는 단순한 리소스 증설(Connection Pool)만으로는 해결되지 않았다.
**비효율적인 쿼리(Full Scan, Filesort)**가 근본 원인이었으며, **인덱스 최적화**와 **Redis 캐싱**을 통해 이를 해결하자 대기열(Pending)이 사라지고 처리량이 2배로 증가하는 극적인 개선 효과를 거두었다.