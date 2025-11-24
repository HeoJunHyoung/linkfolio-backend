package com.example.communityservice.dto.response;

import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class PostResponse {
    private Long id;
    private Long userId;

    // 작성자 정보 (Service에서 채움)
    private String writerName;
    private String writerEmail;

    private PostCategory category;
    private String title;
    private String content;
    private Long viewCount;
    private Long bookmarkCount;
    private Boolean isSolved;
    private RecruitmentStatus recruitmentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    public static PostResponse from(PostEntity entity) {
        return PostResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .category(entity.getCategory())
                .title(entity.getTitle())
                .content(entity.getContent())
                .viewCount(entity.getViewCount())
                .bookmarkCount(entity.getBookmarkCount())
                .isSolved(entity.getCategory() == PostCategory.QNA ? entity.isSolved() : null)
                .recruitmentStatus(entity.getCategory() == PostCategory.RECRUIT ? entity.getRecruitmentStatus() : null)
                .createdAt(entity.getCreatedAt())
                .lastModifiedAt(entity.getLastModifiedAt())
                .build();
    }
}