package com.example.authservice.entity;

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
    @Column(name = "user_id") // [수정] 이 ID를 user-service와 공유
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


    // 생성자
    private AuthUserEntity(String email, String password, UserProvider provider,
                           String username, String providerId, String name) {
        this.email = email;
        this.password = password;
        this.provider = provider;
        this.username = username;
        this.providerId = providerId;
        this.name = name;
    }

    // 'ofLocal' (자체 회원가입용) - username, name 포함
    public static AuthUserEntity ofLocal(String email, String password, String username, String name) {
        return new AuthUserEntity(email, password, UserProvider.LOCAL, username, null, name);
    }

    // 'ofSocial' (소셜 로그인용) - name 포함
    public static AuthUserEntity ofSocial(String email, String password, UserProvider provider, String providerId, String name) {
        // 소셜 유저는 username(로그인 ID)이 없으므로 null
        return new AuthUserEntity(email, password, provider, providerId, null, name);
    }

    public void updatePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }
}