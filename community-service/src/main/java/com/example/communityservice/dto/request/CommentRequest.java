package com.example.communityservice.dto.request;

import lombok.Data;

@Data
public class CommentRequest { // 댓글 작성 및 수정
    private String content;
    private Long parentId; // 대댓글일 경우 부모 댓글 ID (작성 시에만 사용)
}