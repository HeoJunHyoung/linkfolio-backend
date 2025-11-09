package com.example.commonmodule.dto.event;

import com.example.commonmodule.entity.enumerate.Gender;
import com.example.commonmodule.entity.enumerate.Role;
import lombok.Data;

@Data
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
