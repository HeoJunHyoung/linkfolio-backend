package com.example.authservice.entity.enumerate;

public enum AuthStatus {
    PENDING,   // 회원가입 진행 중 (프로필 생성 대기)
    COMPLETED, // SAGA 완료 (프로필 생성 성공)
    CANCELLED  // SAGA 롤백 (프로필 생성 실패)
}