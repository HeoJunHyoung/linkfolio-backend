package com.example.communityservice.dto.response;

import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
public class MyBookmarkPostResponse {
    private Long id;
    private String title;
    private String content; // 요약본
    private PostCategory category;
    private LocalDateTime createdAt;
    private String writerName; // 원 글 작성자 이름

    // QueryDSL Projections.constructor 사용을 위한 생성자
    public MyBookmarkPostResponse(Long id, String title, String content, PostCategory category, LocalDateTime createdAt, String writerName) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.createdAt = createdAt;
        this.writerName = writerName;
    }

    public static MyBookmarkPostResponse from(PostEntity entity) {
        return MyBookmarkPostResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .category(entity.getCategory())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}