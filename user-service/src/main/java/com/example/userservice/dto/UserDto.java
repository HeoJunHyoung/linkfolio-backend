package com.example.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;

    private String email;

    private String password;

    private String nickname;

    public static UserDto of(Long id, String email, String password, String nickname) {
        return new UserDto(id, email, password, nickname);
    }

}
