package com.example.portfolioservice.repository;

import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface PortfolioLikeRepositoryCustom {

    /**
     * 내 관심 포트폴리오 목록을 동적 쿼리(직군, 정렬)로 조회
     */
    Slice<PortfolioCardResponse> searchMyLikedPortfolios(Long likerId, String position, Pageable pageable);
}