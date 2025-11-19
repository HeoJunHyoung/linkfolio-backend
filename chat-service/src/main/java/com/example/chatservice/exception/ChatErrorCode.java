package com.example.chatservice.exception;

import com.example.commonmodule.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_404", "채팅방을 찾을 수 없습니다."),
    CHAT_ROOM_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT_500", "채팅방 생성에 실패했습니다."),
    INVALID_ROOM_PARTNER(HttpStatus.BAD_REQUEST, "CHAT_400", "유효하지 않은 채팅 상대방 ID입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

}