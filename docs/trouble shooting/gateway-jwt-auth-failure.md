# API Gateway JWT 인증 실패 트러블슈팅

* **🗓️ 발생 일시:** 2025년 10월 07일
* **👨‍💻 담당자:** 허준형
* **🏷️ 관련 서비스:** `user-service`, `apigateway-service`

---

## 🐛 이슈 발생

### 현상 요약

`user-service`에서 정상적으로 JWT(토큰)를 발급받은 후, 해당 토큰을 `Authorization` 헤더에 담아 `apigateway-service`를 통해 다른 API(예: `/user-service/users/1`)를 호출하면 401 Unauthorized 오류가 발생하며 요청이 차단됨.

### 재현 순서

1.  `POST /user-service/login`을 통해 정상적으로 로그인하고 `token`을 발급받는다.
2.  `GET /user-service/users/1` 요청의 Headers에 `Authorization: Bearer [발급받은 토큰]`을 추가한다.
3.  요청을 보내면 200 OK가 아닌 401 Unauthorized 응답을 받는다.

---

## 🧐 원인 분석

API Gateway는 토큰이 유효하지 않다고 판단하여 요청을 거부하고 있었음. 근본적인 원인은 토큰을 **생성하는 서비스**와 **검증하는 서비스** 간의 **JWT 라이브러리(jjwt) 버전 불일치** 문제였음.

* **토큰 생성 (`user-service`)**:
    * jjwt 라이브러리 `0.11.5` 버전 사용
    * 구버전의 암호화(서명) 방식으로 토큰 생성
* **토큰 검증 (`apigateway-service`)**:
    * jjwt 라이브러리 `0.12.5` 버전 사용
    * 최신 버전의 복호화(검증) 방식으로 토큰 해독 시도

> 💡 **비유:** 구형 자물쇠(0.11.5)로 잠근 상자를 신형 열쇠(0.12.5)로 열려고 하니, 열쇠가 맞지 않아 "인증 실패"로 판단한 것과 같음.

---

## ✅ 해결 방안

MSA 환경에서 모든 서비스가 동일한 방식으로 토큰을 처리하도록 라이브러리 버전을 통일하고, 최신 버전에 맞는 코드로 수정함.

### 조치 1: `user-service`의 `pom.xml` 의존성 버전 통일

jjwt 관련 라이브러리 버전을 `apigateway-service`와 동일한 `0.12.5`로 상향 조정함.

```
<dependencies>
    ...
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    ...
</dependencies>
```

### 조치 2: user-service의 토큰 생성 코드 수정

jjwt 0.12.x 버전에 맞는 최신 토큰 생성 방식으로 AuthenticationFilter.java의 코드를 수정함.

```
// user-service/src/main/java/com/example/userservice/config/AuthenticationFilter.java

@Override
protected void successfulAuthentication(...) {
    // ...

    // SecretKey 객체를 사용하여 최신 방식으로 토큰 생성
    byte[] secretKeyBytes = env.getProperty("jwt.secret").getBytes(StandardCharsets.UTF_8);
    SecretKey secretKey = Keys.hmacShaKeyFor(secretKeyBytes);

    String token = Jwts.builder()
            .setSubject(userDetails.getUsername())
            .setExpiration(...)
            .signWith(secretKey) // signWith 방식을 최신으로 변경
            .compact();

    // ...
}
```

---
## 📝 후속 조치 및 교훈

* 버전 관리의 중요성: MSA 환경에서는 여러 서비스가 공유하는 라이브러리(특히 인증/보안 관련)의 버전을 반드시 통일해야 한다.

* Parent POM 활용: 향후 유사한 문제를 방지하기 위해, 프로젝트 최상단의 pom.xml에 <dependencyManagement> 섹션을 활용하여 전체 마이크로서비스의 공통 라이브러리 버전을 중앙에서 관리하는 것을 고려한다.