package com.example.userservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private String email;

    private String username; // 아이디

    private String name; // 실명

    private String birthDate;

    private String gender;

    public static UserInfoResponse of(String email, String username, String name, String birthDate, String gender) {
        return new UserInfoResponse(email, username, name, birthDate, gender);
    }
}