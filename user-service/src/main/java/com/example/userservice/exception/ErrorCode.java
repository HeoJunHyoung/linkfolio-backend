package com.example.userservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "회원을 찾을 수 없습니다."),

    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "U002", "비밀번호가 일치하지 않습니다."),

    EMAIL_DUPLICATION(HttpStatus.CONFLICT, "U003", "이미 존재하는 이메일입니다."),
    EMAIL_ALREADY_REGISTERED_AS_SOCIAL(HttpStatus.CONFLICT, "U004", "이미 다른 소셜 계정으로 가입된 이메일입니다."),
    EMAIL_ALREADY_REGISTERED_AS_LOCAL(HttpStatus.CONFLICT, "U005", "이미 로컬 계정으로 가입된 이메일입니다. 로컬 로그인을 이용해주세요."),

    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "T001", "유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "T002", "리프레시 토큰이 일치하지 않습니다. (탈취 의심)"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "T003", "리프레시 토큰을 찾을 수 없습니다. 다시 로그인해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

}
