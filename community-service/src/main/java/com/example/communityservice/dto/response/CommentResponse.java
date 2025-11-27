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
}