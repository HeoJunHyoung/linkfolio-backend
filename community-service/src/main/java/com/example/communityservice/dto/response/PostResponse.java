package com.example.communityservice.dto.response;

import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
public class PostResponse {
    private Long id;
    private Long userId;

    // 작성자 정보
    private String writerName;
    private String writerEmail;

    private PostCategory category;
    private String title;
    private String content;
    private Long viewCount;
    private Long bookmarkCount;
    private Long commentCount;
    private Boolean isSolved;
    private RecruitmentStatus recruitmentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    // QueryDSL Projections.constructor 사용을 위한 생성자
    public PostResponse(Long id, Long userId, String writerName, String writerEmail,
                        PostCategory category, String title, String content,
                        Long viewCount, Long bookmarkCount, Long commentCount, // [추가]
                        Boolean isSolved, RecruitmentStatus recruitmentStatus,
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
        this.recruitmentStatus = recruitmentStatus;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }

    public static PostResponse from(PostEntity entity) {
        return PostResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .category(entity.getCategory())
                .title(entity.getTitle())
                .content(entity.getContent())
                .viewCount(entity.getViewCount())
                .bookmarkCount(entity.getBookmarkCount())
                .commentCount(entity.getCommentCount())
                .isSolved(entity.getCategory() == PostCategory.QNA ? entity.isSolved() : null)
                .recruitmentStatus(entity.getCategory() == PostCategory.RECRUIT ? entity.getRecruitmentStatus() : null)
                .createdAt(entity.getCreatedAt())
                .lastModifiedAt(entity.getLastModifiedAt())
                .build();
    }
}