package com.example.supportservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements com.example.commonmodule.exception.ErrorCode {

    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "G002", "잘못된 입력값입니다."),

    // Notice
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "해당 공지사항을 찾을 수 없습니다."),

    // FAQ
    FAQ_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "해당 FAQ를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}