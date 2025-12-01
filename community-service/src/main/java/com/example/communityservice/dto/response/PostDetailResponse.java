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
    private Long userId;
    private String writerName;
    private String writerEmail;
    private PostCategory category;
    private String title;
    private String content;
    private Long viewCount;
    private Long bookmarkCount;
    private Long commentCount;
    private boolean isSolved;
    private boolean isBookmarked;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @Builder.Default
    private List<CommentResponse> comments = new ArrayList<>();

    // QueryDSL용 생성자 (comments 제외)
    public PostDetailResponse(Long id, Long userId, String writerName, String writerEmail,
                              PostCategory category, String title, String content,
                              Long viewCount, Long bookmarkCount, Long commentCount,
                              boolean isSolved, boolean isBookmarked,
                              LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
        this.id = id;
        this.userId = userId;
        this.writerName = writerName;
        this.writerEmail = writerEmail;
        this.category = category;
        this.title = title;
        this.content = content;
        this.viewCount = viewCount;
        this.bookmarkCount = bookmarkCount;
        this.commentCount = commentCount;
        this.isSolved = isSolved;
        this.isBookmarked = isBookmarked;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }
}