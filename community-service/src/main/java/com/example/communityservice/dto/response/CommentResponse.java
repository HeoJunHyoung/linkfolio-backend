package com.example.communityservice.dto.response;

import com.example.communityservice.entity.PostCommentEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private Long userId;

    // 작성자 정보 (로컬 캐시에서 매핑)
    private String writerName;
    private String writerEmail;

    private String content;
    private boolean isAccepted; // QnA 채택 여부
    private LocalDateTime createdAt;

    // 대댓글 리스트 (계층형 구조)
    private List<CommentResponse> children;

    public static CommentResponse from(PostCommentEntity entity) {
        return CommentResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .content(entity.getContent())
                .isAccepted(entity.isAccepted())
                .createdAt(entity.getCreatedAt())
                .children(new ArrayList<>()) // 초기화
                .build();
    }
}