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

    @Column(name = "password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private UserProvider provider;

    @Column(name = "provider_id")
    private String providerId; // 로컬 가입 시 이 필드는 NULL되어야 함

    private UserEntity(String email, String password, UserProvider provider,
                       String username, String providerId,
                       String name, String birthdate, Gender gender) {
        this.email = email;
        this.password = password;
        this.provider = provider;
        this.username = username;
        this.providerId = providerId;
        this.name = name;
        this.birthdate = birthdate;
        this.gender = gender;
    }

    // 'of' (소셜 로그인용) - 변경된 생성자 호출
    public static UserEntity of(String email, String password, UserProvider provider, String providerId, String name) {
        // username은 null, providerId는 값 존재
        return new UserEntity(email, password, provider, null, providerId, name, null, null);
    }

    // 'ofLocal' (자체 회원가입용)
    public static UserEntity ofLocal(String email, String password, String username, // username(ID)
                                     String name, String birthdate, Gender gender) { // name(실명)
        // username은 값 존재, providerId는 null
        return new UserEntity(email, password,  UserProvider.LOCAL, username, null, name, birthdate, gender);
    }

    public void updatePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }
}