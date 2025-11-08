package com.example.portfolioservice.dto.event;

import com.example.portfolioservice.entity.enumerate.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedEvent {
    private Long userId;
    private String name;
    private String email;
    private String birthdate;
    private Gender gender;
}