package com.example.portfolioservice.client.dto;

import com.example.portfolioservice.entity.enumerate.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InternalUserProfileResponse {
    private Long userId;
    private String email;
    private String name;
    private String birthdate;
    private Gender gender;
}