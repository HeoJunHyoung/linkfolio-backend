package com.example.userservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FindIdRequest {
    
    private String name; // 실명
    private String email; // 본인 확인 이메일
    
}
