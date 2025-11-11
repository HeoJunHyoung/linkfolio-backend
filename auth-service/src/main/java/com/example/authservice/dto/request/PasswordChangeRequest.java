package com.example.authservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordChangeRequest {
    private String currentPassword; // 기존 비밀번호
    private String newPassword;     // 새 비밀번호
    private String newPasswordConfirm; // 새 비밀번호 확인
}