package com.example.communityservice.dto.response;

import com.example.communityservice.entity.enumerate.PostCategory;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailResponse {
    private Long id;
    private Long userId; // 작성자 ID
    private String writerName;
    private String writerEmail;
    private PostCategory category;
    private String title;
    private String content;
    private Long viewCount;
    private Long bookmarkCount;
    private boolean isSolved;
    private boolean isBookmarked; // 로그인 유저의 북마크 여부
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @Builder.Default
    private List<CommentResponse> comments = new ArrayList<>();
}