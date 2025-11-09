package com.example.commonmodule.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 서비스의 ErrorCode Enum이 구현할 공통 인터페이스
 */
public interface ErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}