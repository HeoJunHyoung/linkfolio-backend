package com.example.communityservice.repository;

import com.example.communityservice.dto.response.PostResponse;
import com.example.communityservice.entity.enumerate.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostRepositoryCustom {
    Page<PostResponse> searchPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable);
}
