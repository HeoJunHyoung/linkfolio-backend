package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
    Page<PortfolioEntity> findAllByIsPublished(boolean isPublished, Pageable pageable);
}