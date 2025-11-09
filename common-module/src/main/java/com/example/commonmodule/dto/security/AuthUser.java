package com.example.commonmodule.dto.security;

import com.example.commonmodule.entity.enumerate.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

// UserDetails와 OAuth2User를 모두 구현
@Getter
public class AuthUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final Role role;

    private AuthUser(Long userId, String email, Role role) {
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    // 2. 게이트웨이 헤더 신뢰 시 (InternalHeaderAuthenticationFilter)
    public static AuthUser fromGatewayHeader(Long userId, String email, Role role) {
        return new AuthUser(userId, email, role);
    }

    // --- UserDetails 인터페이스 구현 ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null) {
            return Collections.emptyList();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public String getPassword() {
        return null;
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


}