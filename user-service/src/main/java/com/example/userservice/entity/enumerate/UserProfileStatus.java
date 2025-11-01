package com.example.userservice.entity.enumerate;

// user-service 내부의 프로필 상태
public enum UserProfileStatus {
    PENDING,   // 생성 중 (SAGA 진행 중)
    COMPLETED, // 생성 완료
    FAILED     // 생성 실패
}