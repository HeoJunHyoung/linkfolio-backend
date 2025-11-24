package com.example.communityservice.repository;

import com.example.communityservice.entity.PostCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {
    List<PostCommentEntity> findByPostIdAndParentIsNullOrderByCreatedAtAsc(Long postId);

    @Query("SELECT c FROM PostCommentEntity c WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
    List<PostCommentEntity> findAllByPostIdOrderByCreatedAtAsc(@Param("postId") Long postId);
}
