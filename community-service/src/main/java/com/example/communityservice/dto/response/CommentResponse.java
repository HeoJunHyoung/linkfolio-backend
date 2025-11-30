package com.example.communityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {
    private Long id;
    private Long postId;
    private Long userId; // 작성자 ID
    private String writerName;
    private String writerEmail;
    private String content;
    private boolean isAccepted; // 채택 여부

    @JsonIgnore // 계층 구조 생성용 (클라이언트 응답에는 제외 가능)
    private Long parentId;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @Builder.Default
    private List<CommentResponse> children = new ArrayList<>();

    // QueryDSL Projections.constructor 사용을 위한 생성자 (children 제외)
    public CommentResponse(Long id, Long postId, Long userId, String writerName, String writerEmail,
                           String content, boolean isAccepted, Long parentId,
                           LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.writerName = writerName;
        this.writerEmail = writerEmail;
        this.content = content;
        this.isAccepted = isAccepted;
        this.parentId = parentId;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }
}