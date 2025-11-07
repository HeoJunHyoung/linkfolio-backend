package com.example.portfolioservice.dto.event;

import com.example.portfolioservice.entity.enumerate.Gender;
import com.example.portfolioservice.entity.enumerate.Role;
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