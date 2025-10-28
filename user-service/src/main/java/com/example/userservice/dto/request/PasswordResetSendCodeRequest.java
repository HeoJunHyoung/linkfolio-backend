package com.example.userservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordResetSendCodeRequest {
    private String username; // 아이디
    private String email; // 본인 확인 이메일
}
