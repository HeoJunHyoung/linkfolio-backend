## 📌 개요
| 이번 PR의 핵심 변경 사항을 한 문장으로 요약하여 작성해주세요.

ex) JWT 기반의 자체/소셜 로그인, 토큰 재발급, 로그아웃 등 핵심 인증/인가 기능을 구현합니다.

---
## 📋 작업 내용
| 이번 PR에서 작업한 내용들을 작성해주세요.

ex)
1. Spring Security 기본 설정 및 JWT 검증을 위한 JWTFilter 추가
2. JSON 기반 자체 로그인을 위한 LoginFilter 및 LocalLoginSuccessHandler 구현
3. 소셜 로그인(Google, Naver) 처리를 위한 SocialLoginService, SocialLoginSuccessHandler 구현
4. Refresh Token Rotation 전략을 적용한 토큰 재발급 API (/jwt/refresh) 구현
5. Refresh Token을 DB에서 삭제하는 로그아웃 핸들러(RefreshTokenLogoutHandler) 추가

---
## 🔗 관련 이슈
| GitHub 이슈 번호가 있다면 Closes #이슈번호 형식으로 태그해주세요.

ex) Closes #15

```
💡 이슈 태그란?
Closes, Fixes, Resolves 같은 키워드를 이슈 번호 앞에 붙이면, 이 Pull Request가 머지(Merge)될 때 해당 이슈가 자동으로 닫힙니다.
예를 들어, Closes #15 라고 적으면, 이 PR이 dev 브랜치에 머지되는 순간 GitHub가 알아서 15번 이슈를 "Closed" 상태로 변경해줍니다. 프로젝트 관리가 매우 편리해지는 기능입니다.
```

---
## ✅ PR 체크리스트
- [ ] 기능이 정상 동작함
- [ ] 불필요한 코드/콘솔 제거함
- [ ] 스타일/포맷팅 문제 없음

---
## 💬 기타 사항

ex)
- Refresh Token 저장소로 초기에는 MySQL을 사용했으나, 리뷰 후 Redis로 전환할 예정입니다.
- JWTUtil 클래스의 토큰 생성 및 검증 로직이 보안적으로 민감한 부분이 존재합니다.