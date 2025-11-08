package com.example.userservice.entity;

import com.example.userservice.dto.event.UserRegistrationRequestedEvent;
import com.example.userservice.entity.enumerate.Gender;
import com.example.userservice.entity.enumerate.Role;
import com.example.userservice.entity.enumerate.UserProfileStatus;
import com.example.userservice.entity.enumerate.UserProvider;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserProfileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // 생성자
    private UserProfileEntity(Long userId, String email, UserProvider provider, String username,
                              String name, String birthdate, Gender gender, UserProfileStatus status, Role role) {
        this.userId = userId;
        this.email = email;
        this.provider = provider;
        this.username = username;
        this.name = name;
        this.birthdate = birthdate;
        this.gender = gender;
        this.status = status;
        this.role = role;
    }

    // Kafka Consumer용 생성 메서드
    public static UserProfileEntity fromEvent(UserRegistrationRequestedEvent event) {
        return new UserProfileEntity(
                event.getUserId(),
                event.getEmail(),
                UserProvider.valueOf(event.getProvider()),
                event.getUsername(),
                event.getName(),
                event.getBirthdate(),
                event.getGender(),
                UserProfileStatus.PENDING, // 최초 상태는 PENDING
                event.getRole()
        );
    }

    // 상태 업데이트 메서드
    public void updateStatus(UserProfileStatus status) {
        this.status = status;
    }

    public void updateUserProfile(String name, String birthdate, Gender gender) {
        this.name = name;
        this.birthdate = birthdate;
        this.gender = gender;
    }

}