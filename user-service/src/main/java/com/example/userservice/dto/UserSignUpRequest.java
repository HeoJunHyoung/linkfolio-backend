package com.example.userservice.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSignUpRequest {

    private String email;

    private String password;

    private String passwordConfirm;

}
