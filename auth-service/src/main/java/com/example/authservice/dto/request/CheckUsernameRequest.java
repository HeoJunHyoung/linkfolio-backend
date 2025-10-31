package com.example.authservice.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CheckUsernameRequest {
    private String username;
}
