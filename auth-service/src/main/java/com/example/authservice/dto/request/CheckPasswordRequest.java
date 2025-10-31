package com.example.authservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CheckPasswordRequest {
    private String password;
    private String passwordConfirm;
}