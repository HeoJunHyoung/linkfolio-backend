package com.example.authservice.dto.request;

import com.example.authservice.entity.enumerate.Gender;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSignUpRequest {

    private String email;

    private String username; // 로그인 ID

    private String password;

    private String passwordConfirm;

    private String name; // 실명

    private String birthdate;

    private Gender gender;

}
