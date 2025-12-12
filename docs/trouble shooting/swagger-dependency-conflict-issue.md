# SpringDoc과 Kafka Avro Serializer 간 Swagger 버전 충돌 해결

* **🗓️ 발생 일시:** 2025/11/25
* **👨‍💻 담당자:** 허준형
* **🏷️ 관련 서비스:** `user-service`, `auth-service`, `portfolio-service`, `community-service`, `chat-service`

---

## 🐛 이슈 발생

### 현상 요약

`support-service`를 제외한 나머지 5개 서비스(`user`, `auth`, `portfolio`, `community`, `chat`) 구동 후 Swagger UI (`/webjars/swagger-ui/index.html`) 접근 시, 500 Internal Server Error가 발생함.

서버 로그 확인 결과, `GlobalExceptionHandler`에서 `java.lang.NoSuchMethodError` 예외가 포착됨.

```text
java.lang.NoSuchMethodError: 'io.swagger.v3.oas.annotations.media.SchemaProperty[] io.swagger.v3.oas.annotations.media.Content.schemaProperties()'
    at org.springdoc.core.utils.SpringDocAnnotationsUtils.getMediaType(...)
...
```

### 재현 순서

1. `user-service` 등 Kafka를 사용하는 서비스를 실행한다.
2. 브라우저에서 Swagger UI 엔드포인트(`/webjars/swagger-ui/index.html`)로 접속한다.
3. 페이지 로드에 실패하며 서버 로그에 `NoSuchMethodError`가 출력된다.
4. 반면, Kafka 의존성이 없는 `support-service`는 정상적으로 Swagger UI가 표시된다.

---

## 🧐 원인 분석

- **라이브러리 버전 충돌 (Dependency Conflict)**
  - 프로젝트에서 API 문서화를 위해 사용 중인 springdoc-openapi-starter-webmvc-ui:2.5.0 은 최신 버전의 Swagger 라이브러리(v2.2.x 이상)를 필요로 함.
  - 하지만, Kafka 메시지 직렬화를 위해 추가한 io.confluent:kafka-avro-serializer:7.6.1 라이브러리가 내부적으로(Transitive Dependency) 구버전의 Swagger 라이브러리를 의존성으로 가지고 있음.

- **메서드 부재**
  - 빌드 도구(Maven)가 의존성을 해결하는 과정에서 kafka-avro-serializer가 가져온 구버전 Swagger 어노테이션 라이브러리가 로드됨.
  - 런타임 시 SpringDoc이 Content.schemaProperties() 메서드를 호출하려 했으나, 로드된 구버전 라이브러리에는 해당 메서드가 존재하지 않아 NoSuchMethodError가 발생함.

---

## ✅ 해결 방안

### 조치 1: 불필요한 Transitive Dependency 제외 (Exclusion)

`kafka-avro-serializer`는 Avro 직렬화가 주 목적이므로, 내부적으로 참조하는 Swagger 관련 라이브러리는 서비스 구동에 불필요함. 따라서 pom.xml에서 해당 의존성을 명시적으로 제외(exclude) 처리함.

대상 파일: 각 서비스의 `pom.xml` (user, auth, portfolio, community, chat)

```
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>7.6.1</version>
    <exclusions>
        <exclusion>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## 📝 후속 조치 및 교훈

**의존성 관리의 중요성**: 외부 라이브러리를 추가할 때, 해당 라이브러리가 내부적으로 어떤 의존성을 가져오는지(Transitive Dependencies) 주의 깊게 확인해야 함.

**불필요한 의존성 제거**: 라이브러리의 주 목적과 관계없는 부가적인 의존성이 충돌을 일으킬 경우, 버전을 강제로 맞추기보다는 exclusion을 통해 제거하는 것이 의존성 트리를 가볍게 유지하는 데 유리함.

**환경 격리 확인**: `support-service`에서 에러가 발생하지 않았던 점을 통해, 특정 라이브러리(`kafka-avro-serializer`)가 원인임을 빠르게 좁힐 수 있었음.