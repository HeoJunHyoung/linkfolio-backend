package com.example.authservice.dto;

import com.example.authservice.entity.enumerate.Role;
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

    private Role role;

    public static UserDto of(Long id, String email, String password, Role role) {
        return new UserDto(id, email, password, role);
    }

}
