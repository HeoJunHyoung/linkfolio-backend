package com.example.authservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordResetSendCodeRequest {
    private String email; // 본인 확인 이메일
}
