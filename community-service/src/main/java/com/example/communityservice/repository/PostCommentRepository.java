package com.example.communityservice.repository;

import com.example.communityservice.entity.PostCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {
    List<PostCommentEntity> findByPostIdAndParentIsNullOrderByCreatedAtAsc(Long postId);
}
