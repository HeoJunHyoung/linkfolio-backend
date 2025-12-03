package com.example.commonmodule.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileCreationFailureEvent {
    private Long userId;
    private String reason;
}