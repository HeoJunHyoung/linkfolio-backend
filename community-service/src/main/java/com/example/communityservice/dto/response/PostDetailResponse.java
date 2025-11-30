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

    // QueryDSL Projections.constructor 사용을 위한 생성자 (comments 제외)
    public PostDetailResponse(Long id, Long userId, String writerName, String writerEmail,
                              PostCategory category, String title, String content,
                              Long viewCount, Long bookmarkCount, boolean isSolved,
                              boolean isBookmarked, LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
        this.id = id;
        this.userId = userId;
        this.writerName = writerName;
        this.writerEmail = writerEmail;
        this.category = category;
        this.title = title;
        this.content = content;
        this.viewCount = viewCount;
        this.bookmarkCount = bookmarkCount;
        this.isSolved = isSolved;
        this.isBookmarked = isBookmarked;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }
}