package com.example.communityservice.dto.response;

import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MyPostResponse { // 내가 쓴 글 반환 목적 DTO
    private Long id;
    private String title;
    private String content;
    private PostCategory category;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    // Entity -> DTO 변환 메서드
    public static MyPostResponse from(PostEntity entity) {
        return MyPostResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .category(entity.getCategory())
                .createdAt(entity.getCreatedAt())
                .lastModifiedAt(entity.getLastModifiedAt())
                .build();
    }
}
