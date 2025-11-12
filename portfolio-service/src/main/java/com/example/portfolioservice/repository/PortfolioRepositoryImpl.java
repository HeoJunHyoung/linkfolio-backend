package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.entity.QPortfolioEntity;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

@RequiredArgsConstructor
public class PortfolioRepositoryImpl implements PortfolioRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QPortfolioEntity portfolio = QPortfolioEntity.portfolioEntity;

    @Override
    public Slice<PortfolioEntity> searchPortfolioList(String position, Pageable pageable) {

        List<PortfolioEntity> results = queryFactory
                .selectFrom(portfolio)
                .where(
                        portfolio.isPublished.eq(true), // 1. 기본 조건: 발행된(isPublished) 포트폴리오만
                        positionEq(position) // 2. 동적 조건: position 값이 있으면 필터링
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1) // Slice 처리를 위해 1개 더 조회
                .orderBy(portfolio.createdAt.desc()) // 기본 정렬 (최신순)
                .fetch();

        // SliceImpl로 변환
        boolean hasNext = false;
        if (results.size() > pageable.getPageSize()) {
            results.remove(pageable.getPageSize());
            hasNext = true;
        }

        return new SliceImpl<>(results, pageable, hasNext);
    }

    /**
     * position 파라미터가 null이거나 비어있으면 null을 반환
     * (QueryDSL에서 null인 where 조건은 무시됨)
     */
    private BooleanExpression positionEq(String position) {
        return (position == null || position.isBlank()) ? null : portfolio.position.eq(position);
    }
}