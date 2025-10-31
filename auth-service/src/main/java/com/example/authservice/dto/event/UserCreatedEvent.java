package com.example.authservice.dto.event;

import com.example.authservice.entity.enumerate.Gender;
import lombok.Data;

@Data
public class UserCreatedEvent {
    private Long userId;
    private String email;
    private String username;
    private String name;
    private String birthdate;
    private Gender gender;
    private String provider;
}
