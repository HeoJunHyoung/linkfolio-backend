package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional; // [추가]

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long>, PortfolioRepositoryCustom {
    // userId(소유자ID)로 포트폴리오를 조회하는 메서드
    Optional<PortfolioEntity> findByUserId(Long userId);
}