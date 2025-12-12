package com.example.communityservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements com.example.commonmodule.exception.ErrorCode {

    // 공통
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "G002", "잘못된 입력값입니다."),

    // 게시글 (Post)
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "게시글을 찾을 수 없습니다."),
    NOT_POST_OWNER(HttpStatus.FORBIDDEN, "C002", "게시글 작성자만 해당 작업을 수행할 수 있습니다."),

    // 댓글 (Comment)
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "댓글을 찾을 수 없습니다."),
    NOT_COMMENT_OWNER(HttpStatus.FORBIDDEN, "C008", "댓글 작성자만 해당 작업을 수행할 수 있습니다."),

    // QnA
    NOT_QNA_CATEGORY(HttpStatus.BAD_REQUEST, "C004", "QnA 게시글에서만 답변을 채택할 수 있습니다."),

    // 팀원 모집 (Recruit)
    NOT_RECRUIT_CATEGORY(HttpStatus.BAD_REQUEST, "C005", "팀원 모집 게시글이 아닙니다."),
    RECRUITMENT_CLOSED(HttpStatus.BAD_REQUEST, "C006", "이미 마감된 모집입니다."),

    // 채팅/메시지
    MESSAGE_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "C007", "지원 메시지 전송에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}