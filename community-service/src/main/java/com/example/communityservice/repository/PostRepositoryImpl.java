package com.example.communityservice.repository;

import com.example.communityservice.dto.response.PostResponse;
import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.QPostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QPostEntity post = QPostEntity.postEntity;

    @Override
    public Page<PostResponse> searchPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {

        // 1. 조건에 맞는 게시글 조회 (작성자 정보 조인 제외)
        List<PostEntity> posts = queryFactory
                .selectFrom(post)
                .where(
                        categoryEq(category),
                        keywordContains(keyword),
                        isSolvedEq(isSolved)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc())
                .fetch();

        // 2. 전체 카운트 조회 (페이징 처리를 위해 필요)
        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(
                        categoryEq(category),
                        keywordContains(keyword),
                        isSolvedEq(isSolved)
                )
                .fetchOne();

        if (total == null) total = 0L;

        // 3. Entity -> DTO 변환
        // 작성자 정보(WriterName, WriterEmail)는 Service 계층의 mapWriterInfo()에서 채워지므로 여기서는 변환만 수행
        List<PostResponse> responses = posts.stream()
                .map(PostResponse::from)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, total);
    }

    private BooleanExpression categoryEq(PostCategory category) {
        return category != null ? post.category.eq(category) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return post.title.containsIgnoreCase(keyword)
                .or(post.content.containsIgnoreCase(keyword));
    }

    private BooleanExpression isSolvedEq(Boolean isSolved) {
        return isSolved != null ? post.isSolved.eq(isSolved) : null;
    }
}