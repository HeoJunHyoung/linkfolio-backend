package com.example.communityservice.repository;

import com.example.communityservice.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<PostEntity, Long>, PostRepositoryCustom {
    // 내가 쓴 게시글 조회
    Page<PostEntity> findAllByUserId(Long userId, Pageable pageable);

    // 내가 북마크한 게시글 조회
    @Query("SELECT b.post FROM PostBookmarkEntity b WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    Page<PostEntity> findBookmarkedPosts(@Param("userId") Long userId, Pageable pageable);

    // 내가 댓글 단 게시글 조회
    @Query("SELECT DISTINCT c.post FROM PostCommentEntity c WHERE c.userId = :userId ORDER BY c.post.createdAt DESC")
    Page<PostEntity> findCommentedPosts(@Param("userId") Long userId, Pageable pageable);

    // 조회수 Bulk Update (스케줄러용)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PostEntity p SET p.viewCount = p.viewCount + :count WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id, @Param("count") Long count);
}