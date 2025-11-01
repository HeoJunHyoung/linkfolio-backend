package com.example.authservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileCreationSuccessEvent {
    private Long userId; // SAGA 트랜잭션을 식별하기 위한 ID
}