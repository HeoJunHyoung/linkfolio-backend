package com.example.userservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordResetChangeRequest { // 최종 변경 용도
    private String email; 
    private String newPassword;
    private String passwordConfirm;
}