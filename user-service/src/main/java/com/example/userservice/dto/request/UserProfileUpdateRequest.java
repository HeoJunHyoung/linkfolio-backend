package com.example.userservice.dto.request;

import com.example.commonmodule.entity.enumerate.Gender;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserProfileUpdateRequest {
    // 회원이 수정 가능한 정보 (이메일, ID 등은 제외했음)
    private String name;
    private String birthdate;
    private Gender gender;
}