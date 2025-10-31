package com.example.userservice.entity;

import com.example.userservice.dto.UserCreatedEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "`user_profile`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileEntity extends BaseEntity{

    @Id
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

    // 생성자
    private UserProfileEntity(Long userId, String email, UserProvider provider, String username,
                              String name, String birthdate, Gender gender) {
        this.userId = userId;
        this.email = email;
        this.provider = provider;
        this.username = username;
        this.name = name;
        this.birthdate = birthdate;
        this.gender = gender;
    }

    // Kafka Consumer용 생성 메서드
    public static UserProfileEntity fromEvent(UserCreatedEvent event) {
        return new UserProfileEntity(
                event.getUserId(),
                event.getEmail(),
                UserProvider.valueOf(event.getProvider()),
                event.getUsername(),
                event.getName(),
                event.getBirthdate(),
                event.getGender() // user-service의 Gender enum 사용
        );
    }

}