package com.example.chatservice.client;

import com.example.commonmodule.entity.enumerate.Gender;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InternalUserProfileResponse {
    private Long userId;
    private String email;
    private String name;
    private String birthdate;
    private Gender gender;
}