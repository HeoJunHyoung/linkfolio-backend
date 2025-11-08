// user-service/src/main/java/com/example/userservice/dto/event/UserProfilePublishedEvent.java
package com.example.userservice.dto.event;

import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.entity.enumerate.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfilePublishedEvent {
    private Long userId;
    private String name;
    private String email;
    private String birthdate;
    private Gender gender;

    // Entity에서 DTO로 변환하는 정적 팩토리 메서드
    public static UserProfilePublishedEvent fromEntity(UserProfileEntity entity) {
        return UserProfilePublishedEvent.builder()
                .userId(entity.getUserId())
                .name(entity.getName())
                .email(entity.getEmail())
                .birthdate(entity.getBirthdate())
                .gender(entity.getGender())
                .build();
    }
}