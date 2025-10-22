package com.example.userservice.dto;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

// UserDetails와 OAuth2User를 모두 구현
@Getter
public class AuthUser implements UserDetails, OAuth2User {

    private final Long userId;
    private final String email;
    private final String password; // 로그인 시에만 사용
    // OAuth2 사용자 정보를 담을 필드
    private Map<String, Object> attributes;
    private String nameAttributeKey;


    /**
     * DB 조회를 통해 인증 객체를 생성할 때 사용 (로그인 시)
     */
    public AuthUser(Long userId, String email, String password) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.attributes = null;
        this.nameAttributeKey = null;
    }

    /**
     * 게이트웨이 헤더를 신뢰하여 인증 객체를 생성할 때 사용 (API 요청 시)
     */
    public AuthUser(Long userId, String email) {
        this.userId = userId;
        this.email = email;
        this.password = null; // API 요청 시에는 비밀번호가 필요 없음
        this.attributes = null;
        this.nameAttributeKey = null;
    }

    /**
     * OAuth2 로그인을 통해 인증 객체를 생성할 때 사용
     */
    public AuthUser(Long userId, String email, Map<String, Object> attributes, String nameAttributeKey) {
        this.userId = userId;
        this.email = email;
        this.password = null; // OAuth2는 비밀번호 X
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
    }

    // --- UserDetails 인터페이스 구현 ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // 권한(Role) 미사용
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    // --- 계정 상태 (모두 true) ---
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }


    // --- OAuth2User 인터페이스 구현 ---

    @Override
    public Map<String, Object> getAttributes() {
        // attributes가 null일 경우(로컬 로그인 등) 빈 맵 반환
        return this.attributes != null ? this.attributes : Collections.emptyMap();
    }

    @Override
    public String getName() {
        // OAuth2 공급자가 지정한 name attribute key (sub, id 등)
        // 이 key가 없으면(로컬 로그인 등) userId를 문자열로 반환
        return (this.nameAttributeKey != null && this.attributes != null)
                ? (String) this.attributes.get(this.nameAttributeKey)
                : String.valueOf(this.userId);
    }
}