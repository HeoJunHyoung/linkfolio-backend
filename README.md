<div align="center">
  <img src="images/logo/Linkfolio Logo.png" width="600" alt="LinkFolio Logo"/>
  
  <h1>🔗 LinkFolio</h1>
  <h3>🎯 개발자를 위한 포트폴리오 공유 및 커뮤니티 플랫폼</h3>
  
  <p>
    <b>LinkFolio</b>는 개발자들이 자신의 기술 스택과 프로젝트 경험을 손쉽게 관리하고 공유할 수 있는 플랫폼입니다.<br/>
    단순한 포트폴리오 관리를 넘어, <b>실시간 채팅</b>과 <b>지식 공유</b>를 통해 개발자 생태계를 연결합니다.
  </p>

  <img src="https://img.shields.io/badge/📅%20Project%20Period-2025.10.27%20~%202025.12.12-F0F0F0?style=flat-square&labelColor=black&color=white" alt="Period"/>
  
  <br/><br/>

  <a href="https://github.com/CLD-3rd/Linkfolio-backend.git">
    <img src="https://img.shields.io/badge/Backend-Repository-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Backend Repo"/>
  </a>
  <a href="https://github.com/CLD-3rd/Linkfolio-frontend.git">
    <img src="https://img.shields.io/badge/Frontend-Repository-61DAFB?style=for-the-badge&logo=react&logoColor=black" alt="Frontend Repo"/>
  </a>
  <a href="https://github.com/CLD-3rd/Linkfolio-manifest.git">
    <img src="https://img.shields.io/badge/Manifest-Repository-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white" alt="Manifest Repo"/>
  </a>
</div>
<br/>


## 1️⃣ 팀원 구성 및 역할
<table>
  <tr>
    <th width="500">허준형<br/><sub>Backend Lead & DevOps Engineer</sub></th>
    <th width="500">박성현<br/><sub>Frontend Lead & UI/UX Designer</sub></th>
  </tr>
  <tr>
    <td align="center" valign="top">
      <a href="https://github.com/HeoJunHyoung">
        <img src="https://github.com/HeoJunHyoung.png" width="100" style="border-radius:50%"/><br/>
      </a>
      <br/>
        <b>📦 System Architecture</b> <br/>
        Spring Cloud Gateway 기반 MSA 환경 구축 및 라우팅 설계 <br/>
        Kafka & Debezium(CDC)을 활용한 Event-Driven Architecture 설계 <br/>
        Polyglot Persistence (MySQL, MongoDB, Redis) 데이터 모델링 <br/>
        <br/>
        <b>💻 Backend Development</b> <br/>
        Auth, User, Portfolio, Chat, Support, Community 등 마이크로서비스 전반 구현 </br>
        WebSocket & Redis Pub/Sub을 활용한 실시간 채팅 서비스 개발 </br>
        QueryDSL을 활용한 동적 쿼리 및 검색 기능 최적화</br>
        </br>
        <b>🚀 DevOps & Infrastructure</b> </br>
        On-Premise Kubernetes 클러스터 구축 (Master 1, Worker 2, Kafka VM) </br>
        GitHub Actions & ArgoCD 기반의 GitOps CI/CD 파이프라인 구축 </br>
    </td>
    <td align="center" valign="top">
      <a href="https://github.com/park-sunghyun">
        <img src="https://github.com/park-sunghyun.png" width="100" style="border-radius:50%"/><br/>
      </a>
      <br/>
        <b>[Frontend Development]</b> </br>
        Next.js 13+ 기반의 SSR/CSR 하이브리드 렌더링 구현 </br>
        Recoil/Zustand 등을 활용한 전역 상태 관리 </br>
        WebSocket 클라이언트 연동 및 실시간 채팅 UI 구현 </br>
        </br>
        <b>[UI/UX Design]</b> </br>
        사용자 경험(UX) 중심의 서비스 플로우 설계 </br>
        반응형 웹 디자인 및 인터랙티브 컴포넌트 개발 </br>
    </td>
  </tr>
</table>
<br/><br/>

## 2️⃣ 기술 스택

### 🔧 개발 환경
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **운영 도구** | IntelliJ IDEA Ultimate | - | 백엔드 개발 IDE |
| | Visual Studio Code | - | 프론트엔드 개발 IDE |
| **협업 툴** | Discord | - | 팀 커뮤니케이션 |
| | Gather | - | 가상 협업 공간 |

### 🎯 프론트엔드
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **프레임워크** | Next.js | 13+ | SSR 및 SEO 최적화를 통해 초기 로딩 속도 개선과 검색 엔진 노출 극대화 |
| **기술** | HTML5 / CSS3 / JS | - | 웹 표준 마크업 및 스타일링 |
| **런타임** | Node.js | 18.x (LTS) | 프론트엔드 빌드 및 개발 환경 |

### ⚙️ 백엔드
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **언어** | Java | 17 (LTS) | 최신 LTS 버전의 안정성과 Record 등 모던 Java 문법 활용으로 생산성 향상 |
| **프레임워크** | Spring Boot | 3.5.x | 마이크로서비스 애플리케이션 구축을 위한 표준 프레임워크 |
| | Spring Cloud | 2025.0.0 | API Gateway, OpenFeign 등 MSA 구축에 필수적인 컴포넌트 제공 |
| **보안** | Spring Security | - | JWT 기반 Stateless 인증/인가 및 OAuth2 소셜 로그인 구현 |
| **Query** | QueryDSL | 5.1.0 | 복잡한 검색 조건을 Type-Safe하게 작성하여 런타임 오류 방지 |
| **실시간** | WebSocket / STOMP | - | 실시간 1:1 채팅 기능 구현 |

### 🗄️ 데이터베이스
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **RDBMS** | MySQL | 8.0 | 회원, 인증, 포트폴리오 등 정형 데이터의 ACID 트랜잭션 보장 |
| **NoSQL** | MongoDB | 6.0+ | 채팅 서비스의 대량 쓰기 트래픽 처리 및 비정형 메시지 데이터 유연 저장 |
| **캐시** | Redis | - | 인메모리 고속 I/O를 활용한 Refresh Token 저장, 채팅 Pub/Sub, 인증 코드 캐싱 |

### 📨 메시징 & 이벤트
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **메시징** | Apache Kafka | - | 서비스 간 결합도 감소를 위한 비동기 통신, SAGA 패턴 및 데이터 동기화 처리 |
| **직렬화** | Avro / Schema Registry | - | JSON 대비 페이로드 크기 감소, 스키마 변경 관리 강화 |
| **CDC** | Debezium | - | DB 변경 사항 실시간 감지 및 Kafka 이벤트 자동 발행 |

### 🚀 인프라 & DevOps
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **OS** | Ubuntu | 22.04 LTS | 온프레미스(VM) 환경 호스트 OS로 인프라 제어권 확보 |
| **오케스트레이션** | Kubernetes | - | 컨테이너 오케스트레이션, 오토스케일링 및 자가 치유 환경 구성 |
| **컨테이너** | Docker | - | eclipse-temurin:17 기반 경량 컨테이너 패키징 |
| **CI/CD** | GitHub Actions | - | 소스 코드 푸시 시 빌드, 테스트, Docker 이미지 생성 자동화 |
| **GitOps** | ArgoCD | - | Git 저장소 형상 변경 감지 및 K8s 클러스터 자동 배포 |
| **모니터링** | Prometheus, Grafana | - | 메트릭 수집 및 대시보드 시각화 |
| **테스트** | k6 | - | 성능 및 부하 테스트 |

### 📚 기타 도구
| 구분 | 기술명 | 버전 | 용도 및 선정 이유 |
|------|--------|------|------------------|
| **API 문서화** | Swagger | 2.5.0 | REST API 명세 자동 문서화로 협업 효율 향상 |
| **매핑** | MapStruct | 1.5.5 | Entity와 DTO 간 변환 처리 자동화 |
| **버전 관리** | Git, GitHub | - | 모노레포 전략 기반 형상 관리 |

<br/><br/>
