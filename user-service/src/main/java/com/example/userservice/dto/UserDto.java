package com.example.userservice.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDto {

    private Long id;

    private String email;

    private String password;

    private String nickname;

    private UserDto(Long id, String email, String password, String nickname) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public static UserDto of(Long id, String email, String password, String nickname) {
        return new UserDto(id, email, password, nickname);
    }

}
