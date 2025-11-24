package com.example.communityservice.dto.response;

import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostDetailResponse {
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

    // 상태 값
    private Boolean isSolved;
    private RecruitmentStatus recruitmentStatus;

    // 현재 사용자의 북마크 여부
    private boolean isBookmarked;

    // 댓글 목록 (계층형)
    private List<CommentResponse> comments;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    public static PostDetailResponse from(PostEntity entity) {
        return PostDetailResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .category(entity.getCategory())
                .title(entity.getTitle())
                .content(entity.getContent())
                .viewCount(entity.getViewCount())
                .bookmarkCount(entity.getBookmarkCount())
                .isSolved(entity.isSolved())
                .recruitmentStatus(entity.getRecruitmentStatus())
                .createdAt(entity.getCreatedAt())
                .lastModifiedAt(entity.getLastModifiedAt())
                .build();
    }
}