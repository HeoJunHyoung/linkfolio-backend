package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioLikeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioLikeRepository extends JpaRepository<PortfolioLikeEntity, Long> {

    // 특정 사용자가 특정 포트폴리오를 좋아하는지 확인
    boolean existsByLikerIdAndPortfolio(Long likerId, com.example.portfolioservice.entity.PortfolioEntity portfolio);

    // 관심 취소 시 사용할 삭제 메서드
    Optional<PortfolioLikeEntity> findByLikerIdAndPortfolio(Long likerId, com.example.portfolioservice.entity.PortfolioEntity portfolio);

    // 특정 사용자가 관심 누른 포트폴리오 목록 조회
    Slice<PortfolioLikeEntity> findAllByLikerId(Long likerId, Pageable pageable);
}