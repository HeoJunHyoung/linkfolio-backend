package com.example.userservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordResetConfirmRequest {
    private String username;
    private String code;
    private String newPassword;
    private String passwordConfirm;
}