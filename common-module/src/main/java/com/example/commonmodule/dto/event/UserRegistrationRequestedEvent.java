package com.example.commonmodule.dto.event;

import com.example.commonmodule.entity.enumerate.Gender;
import com.example.commonmodule.entity.enumerate.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class UserRegistrationRequestedEvent {
    private Long userId;
    private String email;
    private String username;
    private String name;
    private String birthdate;
    private Gender gender;
    private String provider;
    private Role role;
}
