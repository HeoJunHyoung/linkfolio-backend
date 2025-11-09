package com.example.apigatewayservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements com.example.commonmodule.exception.ErrorCode {

    MISSING_AUTH_HEADER(HttpStatus.UNAUTHORIZED, "A001", "Authorization 헤더가 누락되었습니다."),
    INVALID_AUTH_FORMAT(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 Authorization 헤더 형식입니다. (Bearer prefix 필요)"),
    INVALID_JWT_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "JWT 토큰이 유효하지 않습니다."),
    INVALID_JWT_PAYLOAD(HttpStatus.UNAUTHORIZED, "A004", "JWT 페이로드(사용자 정보)가 유효하지 않습니다."),
    INTERNAL_FILTER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "A500", "게이트웨이 필터 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}