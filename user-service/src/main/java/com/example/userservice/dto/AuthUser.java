package com.example.userservice.dto;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

// Q. Spring Security에서 기본적으로 제공해주는 User 객체를 사용하면 되는데, 왜 굳이 UserDetails를 상속받는 AuthUser가 필요한가?
// A. 기본적으로 제공되는 User 객체에는 username, password만 받는데 우리는 email이 username으로 사용된다. 즉, @AuthenticationPrincipal에서 가져올 수 있는 값이 email 뿐이다.
//    우리가 유저를 구분하는 값을 userId로 지정한다는 요구사항이 있기 때문에, userId, email을 사용하기 위해서는 UserDeatils를 상속받아서 구현해야 한다.
@Getter
public class AuthUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password; // 로그인 시에만 사용

    /**
     * DB 조회를 통해 인증 객체를 생성할 때 사용 (로그인 시)
     */
    public AuthUser(Long userId, String email, String password) {
        this.userId = userId;
        this.email = email;
        this.password = password;
    }

    /**
     * 게이트웨이 헤더를 신뢰하여 인증 객체를 생성할 때 사용 (API 요청 시)
     */
    public AuthUser(Long userId, String email) {
        this.userId = userId;
        this.email = email;
        this.password = null; // API 요청 시에는 비밀번호가 필요 없음
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


}