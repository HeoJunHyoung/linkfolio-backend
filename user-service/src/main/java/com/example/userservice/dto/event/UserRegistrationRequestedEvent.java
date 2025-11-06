package com.example.userservice.dto.event;

import com.example.userservice.entity.enumerate.Gender;
import com.example.userservice.entity.enumerate.Role;
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
