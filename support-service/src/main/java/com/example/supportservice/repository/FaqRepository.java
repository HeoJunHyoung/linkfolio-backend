package com.example.supportservice.repository;

import com.example.supportservice.entity.FaqEntity;
import com.example.supportservice.entity.enumerate.FaqCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<FaqEntity, Long> {
    // 카테고리별 조회
    List<FaqEntity> findAllByCategory(FaqCategory category);
}