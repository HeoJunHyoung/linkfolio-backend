package com.example.authservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordResetVerifyCodeRequest { // 코드 검증 용도
    private String email;
    private String code; // 비밀번호를 바꿀 수 있는 인증 코드
}