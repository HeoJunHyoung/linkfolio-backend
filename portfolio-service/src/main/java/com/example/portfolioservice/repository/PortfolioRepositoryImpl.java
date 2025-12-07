package com.example.portfolioservice.repository;

import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.entity.QPortfolioEntity;
import com.example.portfolioservice.entity.QPortfolioLikeEntity;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
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
    private final QPortfolioLikeEntity portfolioLike = QPortfolioLikeEntity.portfolioLikeEntity;

    @Override
    public Slice<PortfolioCardResponse> searchPortfolioList(Long userId, String position, Pageable pageable) {

        Expression<Boolean> isLikedExpression = getIsLikedExpression(userId);

        JPAQuery<PortfolioCardResponse> query = queryFactory
                .select(Projections.constructor(PortfolioCardResponse.class,
                        portfolio.userId,
                        portfolio.portfolioId,
                        portfolio.name,
                        portfolio.email,
                        portfolio.position,
                        portfolio.photoUrl,
                        portfolio.oneLiner,
                        portfolio.hashtags,
                        portfolio.viewCount,
                        portfolio.likeCount,
                        isLikedExpression, // 추출한 Expression 주입
                        portfolio.createdAt,
                        portfolio.lastModifiedAt
                ))
                .from(portfolio)
                .where(
                        portfolio.isPublished.eq(true),
                        positionEq(position)
                );

        applySorting(query, pageable.getSort());

        List<PortfolioCardResponse> results = query
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1)
                .fetch();

        boolean hasNext = false;
        if (results.size() > pageable.getPageSize()) {
            results.remove(pageable.getPageSize());
            hasNext = true;
        }

        return new SliceImpl<>(results, pageable, hasNext);
    }

    //==================//
    //== Helper Method==//
    //==================//

    private Expression<Boolean> getIsLikedExpression(Long userId) {
        if (userId == null) {
            // 비로그인: 무조건 false
            return Expressions.constant(false);
        }
        return JPAExpressions.selectOne()
                .from(portfolioLike)
                .where(portfolioLike.portfolio.portfolioId.eq(portfolio.portfolioId)
                        .and(portfolioLike.likerId.eq(userId)))
                .exists();
    }

    /**
     * position 파라미터가 null이거나 비어있으면 null을 반환 (QueryDSL에서 null인 where 조건은 무시됨)
     */
    private BooleanExpression positionEq(String position) {
        return (position == null || position.isBlank()) ? null : portfolio.position.eq(position);
    }

    /**
     * Pageable의 Sort 객체를 QueryDSL의 OrderSpecifier로 변환하여 적용
     */
    private void applySorting(JPAQuery<?> query, Sort sort) {
        if (sort.isUnsorted()) {
            // 정렬 조건이 없으면 기본값 (최신 수정순)
            query.orderBy(portfolio.lastModifiedAt.desc());
            return;
        }

        for (Sort.Order order : sort) {
            Order direction = order.isAscending() ? Order.ASC : Order.DESC;
            String property = order.getProperty();

            OrderSpecifier<?> orderSpecifier;

            // 정렬 가능한 속성을 화이트리스트 방식으로 제한
            switch (property) {
                case "popularityScore": // 인기순
                    orderSpecifier = new OrderSpecifier<>(direction, portfolio.popularityScore);
                    break;
                case "lastModifiedAt": // 최신순 (수정일 기준)
                    orderSpecifier = new OrderSpecifier<>(direction, portfolio.lastModifiedAt);
                    break;
                default:
                    // 허용되지 않은 정렬 속성이면 경고 로그만 남기고 무시 (기본값 최신 수정순 적용)
                    log.warn("Warning: Invalid sort property provided: {}. Defaulting to lastModifiedAt.", property);
                    orderSpecifier = new OrderSpecifier<>(Order.DESC, portfolio.lastModifiedAt);
            }
            query.orderBy(orderSpecifier);
        }
    }

}