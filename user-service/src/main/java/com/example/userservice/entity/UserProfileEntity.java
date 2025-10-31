package com.example.userservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "`user_profile`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileEntity extends BaseEntity{

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

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private UserProvider provider;

    private UserProfileEntity(String email, UserProvider provider, String username,
                              String name, String birthdate, Gender gender) {
        this.email = email;
        this.provider = provider;
        this.username = username;
        this.name = name;
        this.birthdate = birthdate;
        this.gender = gender;
    }


}