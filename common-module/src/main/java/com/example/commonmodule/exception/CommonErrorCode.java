package com.example.commonmodule.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * GlobalExceptionHandler에서 사용할 공통 에러 코드
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다.");
    // 필요시 HttpStatus.BAD_REQUEST 등 다른 공통 에러 추가 가능

    private final HttpStatus status;
    private final String code;
    private final String message;
}