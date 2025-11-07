package com.example.portfolioservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "회원을 찾을 수 없습니다."), // Feign 호출 시 발생 가능

    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "포트폴리오를 찾을 수 없거나 발행되지 않았습니다."),

    USER_NOT_FOUND_FEIGN_FAILED(HttpStatus.NOT_FOUND, "P002", "회원 정보를 조회할 수 없습니다. (user-service 장애 또는 회원 정보 없음)");


    private final HttpStatus status;
    private final String code;
    private final String message;
}