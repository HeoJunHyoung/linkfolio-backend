package com.example.portfolioservice.repository;

import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.entity.QPortfolioEntity;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class PortfolioRepositoryImpl implements PortfolioRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QPortfolioEntity portfolio = QPortfolioEntity.portfolioEntity;

    @Override
    public Slice<PortfolioEntity> searchPortfolioList(String position, Pageable pageable) {

        // 1. 기본 쿼리 생성 (필터링)
        JPAQuery<PortfolioEntity> query = queryFactory
                .selectFrom(portfolio)
                .where(
                        portfolio.isPublished.eq(true), // 기본 조건
                        positionEq(position)            // 동적 조건 (직군)
                );

        // 2. Pageable의 Sort 정보를 기반으로 동적 정렬 적용
        query = applySorting(query, pageable.getSort());

        // 3. 페이징 적용
        List<PortfolioEntity> results = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1) // Slice 처리를 위해 +1
                .fetch();

        // 4. SliceImpl로 변환
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

    /**
     * Pageable의 Sort 객체를 QueryDSL의 OrderSpecifier로 변환하여 적용
     */
    private JPAQuery<PortfolioEntity> applySorting(JPAQuery<PortfolioEntity> query, Sort sort) {
        if (sort.isUnsorted()) {
            // 정렬 조건이 없으면 기본값 (최신순)
            query.orderBy(portfolio.createdAt.desc());
            return query;
        }

        // PathBuilder를 사용하여 문자열 기반의 정렬 속성을 Q-Type 경로로 변환
        PathBuilder<PortfolioEntity> entityPath = new PathBuilder<>(PortfolioEntity.class, "portfolioEntity");

        for (Sort.Order order : sort) {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();

            OrderSpecifier<?> orderSpecifier;

            // 정렬 가능한 속성을 화이트리스트 방식으로 제한
            switch (property) {
                case "createdAt":
                    orderSpecifier = new OrderSpecifier<>(direction, portfolio.createdAt);
                    break;
                case "likeCount":
                    orderSpecifier = new OrderSpecifier<>(direction, portfolio.likeCount);
                    break;
                case "viewCount":
                    orderSpecifier = new OrderSpecifier<>(direction, portfolio.viewCount);
                    break;
                default:
                    // 허용되지 않은 정렬 속성이면 경고 로그만 남기고 무시 (기본값 최신순 적용)
                    log.warn("Warning: Invalid sort property provided: {}. Defaulting to createdAt.", property);
                    orderSpecifier = new OrderSpecifier<>(Order.DESC, portfolio.createdAt);
            }
            query.orderBy(orderSpecifier);
        }
        return query;
    }
}