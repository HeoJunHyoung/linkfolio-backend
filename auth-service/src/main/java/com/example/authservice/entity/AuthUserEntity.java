package com.example.authservice.entity;

import com.example.authservice.entity.enumerate.AuthStatus;
import com.example.authservice.entity.enumerate.Role;
import com.example.authservice.entity.enumerate.UserProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "`auth_user`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthUserEntity extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") // 이 ID를 user-service와 공유
    private Long userId;

    // 로그인할 때 사용하는 ID
    @Column(name = "username", unique = true)
    private String username;

    // 실명
    @Column(name = "name")
    private String name;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private UserProvider provider;

    @Column(name = "provider_id", unique = true)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuthStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // 생성자
    private AuthUserEntity(String email, String password, UserProvider provider,
                           String username, String providerId, String name, AuthStatus status, Role role) {
        this.email = email;
        this.password = password;
        this.provider = provider;
        this.username = username;
        this.providerId = providerId;
        this.name = name;
        this.status = status;
        this.role = role;
    }

    // 'ofLocal' (자체 회원가입용)
    public static AuthUserEntity ofLocal(String email, String password, String username, String name) {
        // SAGA 시작 상태인 PENDING으로 생성
        return new AuthUserEntity(email, password, UserProvider.LOCAL, username, null, name, AuthStatus.PENDING, Role.USER);
    }

    // 'ofSocial' (소셜 로그인용)
    public static AuthUserEntity ofSocial(String email, String password, UserProvider provider, String providerId, String name) {
        // 소셜 로그인은 SAGA와 무관하므로 COMPLETED로 생성
        return new AuthUserEntity(email, password, provider, null, providerId, name, AuthStatus.COMPLETED, Role.USER);
    }

    // SAGA 상태 업데이트용 메서드
    public void updateStatus(AuthStatus status) {
        this.status = status;
    }

    public void updatePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }
}