package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface PortfolioRepositoryCustom {

    /**
     * 포트폴리오 목록을 동적 쿼리(직군)로 조회
     */
    Slice<PortfolioEntity> searchPortfolioList(String position, Pageable pageable);
}