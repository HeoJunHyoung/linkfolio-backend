package com.example.communityservice.repository;

import com.example.communityservice.dto.response.*;
import com.example.communityservice.entity.enumerate.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PostRepositoryCustom {
    // 기존 메서드
    Page<PostResponse> searchPosts(PostCategory category, Boolean isSolved, Pageable pageable);

    // 내가 쓴 글 조회 (경량 DTO)
    Page<MyPostResponse> findMyPosts(Long userId, PostCategory category, Pageable pageable);

    // 내가 북마크한 글 조회 (경량 DTO + 카테고리 분류)
    Page<MyBookmarkPostResponse> findMyBookmarkedPosts(Long userId, PostCategory category, Pageable pageable);

    Optional<PostDetailResponse> findPostDetailById(Long postId, Long loginUserId);

    List<CommentResponse> findCommentsByPostId(Long postId);
}