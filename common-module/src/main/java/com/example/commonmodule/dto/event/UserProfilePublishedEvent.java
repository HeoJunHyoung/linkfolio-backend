// user-service/src/main/java/com/example/userservice/dto/event/UserProfilePublishedEvent.java
package com.example.commonmodule.dto.event;

import com.example.commonmodule.entity.enumerate.Gender;
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

}