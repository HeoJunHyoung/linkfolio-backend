# Redis 캐싱 시 Page<T> 역직렬화 실패 및 ClassCastException 트러블슈팅

* **🗓️ 발생 일시:** 2025년 12월 02일
* **👨‍💻 담당자:** 허준형
* **🏷️ 관련 서비스:** `community-service`, `support-service`

---

## 🐛 이슈 발생

### 현상 요약

게시글 목록(`Page<PostResponse>`) 조회 성능 개선을 위해 Redis 캐싱을 적용하던 중, 캐시된 데이터를 조회할 때 `ClassCastException`이 발생하며 500 Internal Server Error가 반환됨.

구체적으로는 Redis에서 가져온 데이터를 `Page` 객체로 캐스팅하려고 할 때, 해당 데이터가 `PageImpl`이 아닌 `java.util.LinkedHashMap` 타입으로 반환되어 형변환에 실패함.

### 에러 로그
```text
java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to class org.springframework.data.domain.Page
    at com.example.communityservice.service.PostService.getPosts(PostService.java:...)
    ...
```

### 재현 순서

1. `community-service`에서 `Page<PostResponse>`를 반환하는 메서드에 RedisTemplate을 통해 직접 캐싱함.
2. 최초 요청 시에는 DB에서 조회하여 Redis에 저장하므로 정상 응답 (200 OK).
3. 동일한 조건으로 재요청 시, Redis에서 데이터를 조회.
4. Redis에서 가져온 객체(Value)를 Page<PostResponse>로 캐스팅하는 시점에 Casting 실패 오류 발생.

---

## 🧐 원인 분석

- **RedisSerializer의 동작 방식**
    - 현재 프로젝트의 RedisConfig에서는 GenericJackson2JsonRedisSerializer를 사용하여 객체를 JSON으로 직렬화하고 있음.
    - 데이터를 저장할 때는 PageImpl 객체의 필드들이 JSON 문자열로 정상 변환되어 저장됨.
    - 하지만 데이터를 읽어올 때(역직렬화), Jackson 라이브러리는 JSON 구조를 보고 적절한 자바 객체로 매핑을 시도하는데, Page 인터페이스나 PageImpl 구현체에 대한 명확한 타입 정보가 소실되거나 매핑할 수 없는 구조일 경우 기본적으로 LinkedHashMap으로 역직렬화함.

- **PageImpl의 구조적 문제**
    - Spring Data JPA의 PageImpl 객체는 기본 생성자가 없거나, 복잡한 내부 구조를 가지고 있어 Jackson이 타입 정보(@class)를 포함하더라도 완벽하게 원본 객체로 복원하기 어려운 경우가 많음.
    - 결과적으로 RedisTemplate은 반환 타입을 Object로 가져오는데, 실제 들어있는 인스턴스는 LinkedHashMap이 되어버려 소스 코드상의 (Page<PostResponse>) 캐스팅이 실패하게 됨.

---

## ✅ 해결 방안
Redis 캐싱 시에는 `Page<T>`나 `PageImpl<T>` 같은 프레임워크 내부 객체를 직접 캐싱하지 않고, 캐싱에 최적화된 단순한 DTO(POJO)로 변환하여 저장하는 방식을 채택함.

### 조치 1: CustomPageResponse DTO 생성
`Page` 객체에서 필요한 데이터(content 목록, 페이지 정보 등)만 추출하여 담을 수 있는 `CustomPageResponse` 클래스를 생성함. 이 클래스는 기본 생성자와 Getter를 포함하여 직렬화/역직렬화에 문제가 없도록 설계함.

```
// common-module 또는 각 서비스의 dto 패키지
@Getter
@NoArgsConstructor
public class CustomPageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public CustomPageResponse(Page<T> page) {
        this.content = page.getContent();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.last = page.isLast();
    }
}
```
### 조치 2: 서비스 로직 수정
캐싱 대상을 Page<PostResponse>에서 CustomPageResponse<PostResponse>로 변경함.
```
// 수정 전 (문제 발생 코드)
// public Page<PostResponse> getPosts(...) { ... }

// 수정 후
public CustomPageResponse<PostResponse> getPosts(PostCategory category, Pageable pageable) {
    String cacheKey = "posts:" + category + ":" + pageable.getPageNumber();
    
    // 1. 캐시 조회
    CustomPageResponse<PostResponse> cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;
    }

    // 2. DB 조회 및 DTO 변환
    Page<PostResponse> page = postRepository.searchPosts(category, pageable);
    CustomPageResponse<PostResponse> response = new CustomPageResponse<>(page);

    // 3. 캐시 저장
    redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(10));
    
    return response;
}
```
---

## 📝 후속 조치 및 교훈

- **DTO 사용의 중요성**: 외부 라이브러리나 프레임워크의 내부 구현체(Page, HttpServletRequest 등)를 그대로 캐싱하거나 메시지 큐에 태우는 것은 지양해야 한다. 언제나 우리가 제어 가능한 DTO(Data Transfer Object)로 변환하여 시스템 간 의존성을 줄이고 직렬화 안정성을 확보해야 한다.
- **Redis 직렬화 전략**: GenericJackson2JsonRedisSerializer는 편리하지만, 제네릭 타입이나 복잡한 객체 그래프를 다룰 때는 예상치 못한 역직렬화 이슈가 발생할 수 있음을 인지하고 있어야 한다.