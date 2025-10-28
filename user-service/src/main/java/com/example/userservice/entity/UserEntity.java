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

    // DB에 Increment로 저장되는 ID값
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    // 로그인할 때 사용하는 ID
    @Column(name = "username", unique = true)
    private String username;

    // 실명
    @Column(name = "name")
    private String name;

    @Column(name = "birthdate")
    private String birthdate;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "email", unique = true)
    private String email;

    // 앱 내부에서 사용되는 닉네임
    @Column(name = "nickname", unique = true)
    private String nickname;

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private UserProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    private UserEntity(String email, String password, String nickname, UserProvider provider, String providerId,
                       String username, String name, String birthdate, Gender gender) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.username = username;
        this.name = name; // [추가]
        this.birthdate = birthdate;
        this.gender = gender;
    }

    public static UserEntity of(String email, String password, String nickname, UserProvider provider, String providerId, String name) {
        return new UserEntity(email, password, nickname, provider, providerId,
                null, name, null, null);
    }
    public static UserEntity ofLocal(String email, String password, String nickname,
                                     String username, String name, String birthdate, Gender gender) {
        return new UserEntity(email, password, nickname, UserProvider.LOCAL, null,
                username, name, birthdate, gender); 
    }
}
