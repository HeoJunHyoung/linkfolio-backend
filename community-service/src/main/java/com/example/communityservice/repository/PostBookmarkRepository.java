package com.example.communityservice.repository;

import com.example.communityservice.entity.PostBookmarkEntity;
import com.example.communityservice.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostBookmarkRepository extends JpaRepository<PostBookmarkEntity, Long> {
    boolean existsByPostAndUserId(PostEntity post, Long userId);
    Optional<PostBookmarkEntity> findByPostAndUserId(PostEntity post, Long userId);
}