# LinkFolio

<p align="center">
  <img src="images/logo/Linkfolio Logo.png" width="750" alt="LinkFolio Logo"/>
</p>

## 🧑‍💻 개발자를 위한 포트폴리오 공유 및 커뮤니티 플랫폼

### 프로젝트 개요
**LinkFolio**는 개발자를 위한 종합 포트폴리오 관리 및 커뮤니티 플랫폼입니다. 사용자는 기술 스택과 프로젝트 경험을 체계적으로 관리하고 공유할 수 있으며, 실시간 채팅을 통한 협업과 지식 공유가 가능한 개발자 생태계를 제공합니다.

### 핵심 기술 및 특징
- **MSA 아키텍처**: 서비스별 독립적인 배포 및 확장이 용이하도록 Spring Cloud 기반의 마이크로서비스 아키텍처 설계
- **실시간 커뮤니케이션**: WebSocket과 Redis Pub/Sub을 활용하여 대규모 트래픽 환경에서도 안정적인 1:1 실시간 채팅 기능 구현
- **데이터 동기화**: Kafka와 Debezium(CDC)을 활용한 이벤트 기반 아키텍처(EDA)로 서비스 간 데이터 일관성 유지 및 결합도 감소
- **고성능 검색**: QueryDSL을 도입하여 복잡한 조건의 동적 쿼리를 최적화하고 검색 성능 향상
- **보안 강화**: Spring Security와 JWT를 이용한 Stateless 인증/인가 시스템 구축 및 OAuth2 소셜 로그인 지원
- **DevOps 자동화**: GitHub Actions와 ArgoCD를 연동한 GitOps 기반 CI/CD 파이프라인 구축으로 배포 효율성 증대

### 주요 기능
- **포트폴리오 관리**: 마크다운 에디터를 활용한 포트폴리오 작성 및 수정, PDF 내보내기 
- **실시간 채팅**: 사용자 간 1:1 실시간 채팅, 안 읽은 메시지 카운트, 채팅방 목록 관리 
- **커뮤니티**: 기술 Q&A, 정보 공유, 프로젝트 팀원 모집 게시판 제공 
- **필터링**: 기술 스택, 직군 등 다양한 조건을 활용한 포트폴리오 및 게시글 탐색
- **알림** 시스템: 채팅, 댓글 등 주요 이벤트 발생 시 실시간 알림 제공

***

## 🔗 깃허브 링크
| 분류 | 링크 |
|:---:|:---|
| **BackEnd** | [https://github.com/HeoJunHyoung/Linkfolio-backend](https://github.com/HeoJunHyoung/Linkfolio-backend) |
| **FrontEnd** | [https://github.com/CLD-3rd/Linkfolio-frontend](https://github.com/CLD-3rd/Linkfolio-frontend) |
| **Manifest** | [https://github.com/HeoJunHyoung/Linkfolio-manifest](https://github.com/HeoJunHyoung/Linkfolio-manifest) |

---

## 1️⃣ 팀원 구성 및 역할

<table>
  <tr>
    <td align="center" width="50%">
      <a href="https://github.com/HeoJunHyoung">
        <img src="https://github.com/HeoJunHyoung.png" width="120" style="border-radius:50%;"/>
      </a>
      <br/>
      <h3>허준형</h3>
    </td>
    <td align="center" width="50%">
      <a href="https://github.com/park-sunghyun">
        <img src="https://github.com/park-sunghyun.png" width="120" style="border-radius:50%;"/>
      </a>
      <br/>
      <h3>박성현</h3>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <br/>
      <b>🛠 Architecture & DevOps</b><br/>
      - MSA 환경 구축 및 시스템 아키텍처 설계<br/>
      - On-Premise Kubernetes 클러스터 구축<br/>
      - GitHub Actions & ArgoCD 기반 GitOps 구현<br/>
      <br/>
      <b>💻 Backend Development</b><br/>
      - Kafka 기반 Event-Driven 데이터 동기화 처리<br/>
      - Polyglot Persistence (MySQL, MongoDB, Redis) 설계<br/>
    </td>
    <td valign="top">
      <br/>
      <b>🎨 Frontend Development</b><br/>
      - Next.js 기반 웹 클라이언트 아키텍처 설계<br/>
      - 전역 상태 관리 및 렌더링 최적화 (SSR/CSR)<br/>
      - WebSocket 클라이언트 연동 및 실시간 처리<br/>
      <br/>
      <b>✨ UI/UX Design</b><br/>
      - 사용자 중심 서비스 플로우 및 인터페이스 설계<br/>
      - 반응형 웹 디자인 및 인터랙티브 컴포넌트 개발<br/>
      <br/>
    </td>
  </tr>
</table>

## 2️⃣ 기술 스택

| Category | Technology |
| :--- | :--- |
| **Frontend** | ![Next.js](https://img.shields.io/badge/Next.js-000000?style=flat-square&logo=next.js&logoColor=white) ![Node.js](https://img.shields.io/badge/Node.js-339933?style=flat-square&logo=node.js&logoColor=white) ![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=flat-square&logo=html5&logoColor=white) ![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=flat-square&logo=css3&logoColor=white) ![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=flat-square&logo=javascript&logoColor=black) |
| **Backend** | ![Java](https://img.shields.io/badge/Java-007396?style=flat-square&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-6DB33F?style=flat-square&logo=spring&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white) ![QueryDSL](https://img.shields.io/badge/QueryDSL-007396?style=flat-square) ![WebSocket](https://img.shields.io/badge/WebSocket-000000?style=flat-square&logo=socket.io&logoColor=white) |
| **Database** | ![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white) ![MongoDB](https://img.shields.io/badge/MongoDB-47A248?style=flat-square&logo=mongodb&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white) |
| **Messaging** | ![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white) ![Debezium](https://img.shields.io/badge/Debezium-000000?style=flat-square) ![Avro](https://img.shields.io/badge/Avro-231F20?style=flat-square&logo=apache) |
| **Infra & DevOps** | ![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white) ![Ubuntu](https://img.shields.io/badge/Ubuntu-E95420?style=flat-square&logo=ubuntu&logoColor=white) ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white) ![ArgoCD](https://img.shields.io/badge/ArgoCD-EF7B4D?style=flat-square&logo=argo&logoColor=white) |
| **Monitoring** | ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white) ![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white) ![k6](https://img.shields.io/badge/k6-7D64FF?style=flat-square&logo=k6&logoColor=white) |
| **Tools** | ![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-000000?style=flat-square&logo=intellijidea&logoColor=white) ![VS Code](https://img.shields.io/badge/VS_Code-007ACC?style=flat-square&logo=visualstudiocode&logoColor=white) ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black) ![Git](https://img.shields.io/badge/Git-F05032?style=flat-square&logo=git&logoColor=white) |


