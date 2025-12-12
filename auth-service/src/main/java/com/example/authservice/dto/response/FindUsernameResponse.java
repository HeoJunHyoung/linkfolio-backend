package com.example.authservice.dto.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FindUsernameResponse {

    private String username;

    private FindUsernameResponse(String username) {
        this.username = username;
    }

    // 정적 팩토리 메서드
    public static FindUsernameResponse of(String username) {
        return new FindUsernameResponse(username);
    }
}
