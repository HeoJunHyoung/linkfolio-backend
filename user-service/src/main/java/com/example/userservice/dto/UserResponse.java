package com.example.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;

    private String email;

    private String nickname;

    public static UserResponse of(Long id, String email, String nickname) {
        return new UserResponse(id, email, nickname);
    }
}
