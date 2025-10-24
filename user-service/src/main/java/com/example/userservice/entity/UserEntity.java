package com.example.userservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "`user`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "nickname", unique = true)
    private String nickname;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private UserProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    private UserEntity(String email, String nickname, String password, UserProvider provider, String providerId) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static UserEntity of(String email, String password, String nickname, UserProvider provider, String providerId) {
        return new UserEntity(email, password, nickname, provider, providerId);
    }
}
