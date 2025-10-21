package com.example.userservice.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserResponse {

    private Long id;

    private String email;

    private String nickname;

    public static UserResponse of(Long id, String email, String nickname) {
        return new UserResponse(id, email, nickname);
    }
}
