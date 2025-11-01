package com.example.userservice.dto;

import com.example.userservice.entity.enumerate.Gender;
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
