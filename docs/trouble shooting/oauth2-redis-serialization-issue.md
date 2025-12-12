# 소셜 로그인 콜백 401 Unauthorized 트러블슈팅

* **🗓️ 발생 일시:** 2025/10/30
* **👨‍💻 담당자:** 허준형
* **🏷️ 관련 서비스:** `user-service`, `apigateway-service`

---

## 🐛 이슈 발생

### 현상 요약

쿠버네티스 환경에서 소셜 로그인(카카오, 구글) 시도 시, 인증 콜백(Callback) 과정에서 `apigateway-service`가 401 Unauthorized (`Authorization` 헤더가 누락되었습니다) 오류를 반환함.

`user-service`의 로그(DEBUG 레벨) 확인 결과, 소셜 로그인 콜백 요청(`.../login/oauth2/code/...`)을 받은 직후, 인증 실패 시의 기본 경로인 `/user-service/login?error`로 리디렉션하고 있었음.

DB의 `user` 테이블에는 어떠한 데이터도 저장되지 않았으며, `GlobalExceptionHandler`에 의한 ERROR 로그가 전혀 남지 않았음.

### 재현 순서

1.  `user-service`가 2개 이상의 Pod로 실행 중인 환경에서 소셜 로그인 시도.
2.  로그인 버튼 클릭 (예: `.../oauth2/authorization/kakao`). (이때 `user-pod-A`가 요청을 처리)
3.  카카오/구글 서버에서 인증 완료 후, `redirect-uri`로 콜백. (로드밸런서에 의해 `user-pod-B`가 요청을 처리)
4.  `user-pod-B`가 인증에 실패하고 `/user-service/login?error`로 리디렉션 응답을 보냄.
5.  브라우저는 이 리디렉션 응답에 따라 `apigateway-service`로 `GET /user-service/login` 요청을 전송.
6.  `apigateway-service`는 이 경로가 인증 예외 목록에 없으므로 `AuthorizationHeaderFilter`를 실행하고, `Authorization` 헤더가 없는 해당 요청에 401 Unauthorized를 반환함.

---

## 🧐 원인 분석

* **중앙 세션 관리 로직:** 다중 Pod(Stateless) 환경에서 OAuth2의 `state` 값을 공유하기 위해, `RedisBasedAuthorizationRequestRepository`를 구현하여 사용함. 이 로직은 `state`가 포함된 `OAuth2AuthorizationRequest` 객체를 Redis에 저장함.

* **부적절한 직렬화 방식 (JSON):** `RedisConfig`에 `RedisTemplate`의 `valueSerializer`로 `GenericJackson2JsonRedisSerializer` (JSON 직렬화)가 설정되어 있었음.

* **역직렬화(Deserialization) 실패:**
    * 이메일 인증 코드, Refresh 토큰 등 단순 `String` 객체는 JSON 직렬화/역직렬화에 문제가 없었음.
    * 하지만 Spring Security의 `OAuth2AuthorizationRequest` 객체는 기본 생성자가 없는 복잡한 객체임.
    * `user-pod-A`가 이 객체를 JSON으로 직렬화하여 Redis에 저장하는 것은 성공했으나 (Redis `keys *` 명령어로 확인됨), `user-pod-B`가 콜백을 받아 이 JSON 문자열을 다시 `OAuth2AuthorizationRequest` 객체로 역직렬화하는 데 실패함.

* **최종 실패 흐름:**
    * `RedisBasedAuthorizationRequestRepository`의 `loadAuthorizationRequest` 메서드 내 `redisTemplate.opsForValue().get(redisKey)`가 역직렬화 실패로 `null`을 반환함.
    * Spring Security의 인증 필터는 `null`이 반환되자 `state` 검증(CSRF 방어)에 실패한 것으로 간주하여 '인증 실패' 처리함.
    * 이 실패는 `GlobalExceptionHandler`에 도달하기 전에 인증 필터 단에서 처리되어, ERROR 로그 없이 `/login?error` 리디렉션으로 이어진 것임.

---

## ✅ 해결 방안

### 조치 1: RedisConfig의 직렬화 방식 변경

`user-service`의 `RedisConfig.java` 파일의 `redisTemplate` Bean 설정을 JSON 직렬화 방식(`GenericJackson2JsonRedisSerializer`)에서 Java 기본 직렬화 방식(`JdkSerializationRedisSerializer`)으로 변경함.

`OAuth2AuthorizationRequest` 객체는 `Serializable` 인터페이스를 구현하고 있으므로, `JdkSerializationRedisSerializer`는 이 객체를 안정적으로 직렬화하고 역직렬화할 수 있음.

---

## 📝 후속 조치 및 교훈

* `RedisTemplate`을 공통으로 사용할 때, 저장되는 데이터 타입(`String`과 복잡한 `Serializable` 객체)을 모두 고려하여 Serializer를 선택해야 함.
* `GenericJackson2JsonRedisSerializer`는 기본 생성자가 없는 객체의 역직렬화에 실패할 수 있으므로, Spring Security의 내장 객체 등을 저장할 때는 `JdkSerializationRedisSerializer` 사용을 우선적으로 고려함.
* Spring Security 필터 단에서 발생하는 인증 실패는 `GlobalExceptionHandler`에 도달하지 않고 DEBUG 레벨의 리디렉션 로그로만 나타날 수 있으므로, 인증 문제 발생 시 로깅 레벨을 조정하여 추적해야 함.