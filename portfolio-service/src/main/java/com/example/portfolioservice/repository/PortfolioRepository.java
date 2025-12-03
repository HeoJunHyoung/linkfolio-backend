package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional; // [추가]

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long>, PortfolioRepositoryCustom {
    // userId(소유자ID)로 포트폴리오를 조회하는 메서드
    Optional<PortfolioEntity> findByUserId(Long userId);

    // 조회수 Bulk Update
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PortfolioEntity p SET p.viewCount = p.viewCount + :count WHERE p.portfolioId = :id")
    void incrementViewCount(@Param("id") Long id, @Param("count") Long count);

    // 좋아요 수 Bulk Update
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PortfolioEntity p SET p.likeCount = p.likeCount + :delta WHERE p.portfolioId = :id")
    void updateLikeCount(@Param("id") Long id, @Param("delta") Long delta);

    // 인기 점수 Bulk Update
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE portfolio p SET p.popularity_score = " +
            "CAST((p.view_count + p.like_count * 50) * 1000 / POW(TIMESTAMPDIFF(HOUR, IFNULL(p.last_modified_at, NOW()), NOW()) + 2, 1.5) AS UNSIGNED)",
            nativeQuery = true)
    void updateAllPopularityScores();
}