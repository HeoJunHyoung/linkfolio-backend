package com.example.communityservice.repository;

import com.example.communityservice.dto.response.*;
import com.example.communityservice.entity.*;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QPostEntity post = QPostEntity.postEntity;
    private final QPostBookmarkEntity postBookmark = QPostBookmarkEntity.postBookmarkEntity;
    private final QPostUserProfileEntity userProfile = QPostUserProfileEntity.postUserProfileEntity;
    private final QPostCommentEntity postComment = QPostCommentEntity.postCommentEntity;


    // 1. 게시글 목록 조회 (작성자 정보 Join 추가)
    @Override
    public Page<PostResponse> searchPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
        List<PostResponse> content = queryFactory
                .select(Projections.fields(PostResponse.class,
                        post.id,
                        post.userId,
                        post.category,
                        post.title,
                        post.content,
                        post.viewCount,
                        post.bookmarkCount,
                        post.isSolved,
                        post.createdAt,
                        post.lastModifiedAt,
                        userProfile.name.as("writerName"),  // Join된 작성자 이름
                        userProfile.email.as("writerEmail") // Join된 작성자 이메일
                ))
                .from(post)
                .leftJoin(userProfile).on(post.userId.eq(userProfile.userId)) // 작성자 조인
                .where(
                        categoryEq(category),
                        keywordContains(keyword),
                        isSolvedEq(isSolved)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc())
                .fetch();

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

        return new PageImpl<>(content, pageable, total);
    }

    // 게시글 상세 조회 (작성자 Join + 북마크 여부 확인)
    @Override
    public Optional<PostDetailResponse> findPostDetailById(Long postId, Long loginUserId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.fields(PostDetailResponse.class,
                        post.id,
                        post.userId,
                        userProfile.name.as("writerName"),
                        userProfile.email.as("writerEmail"),
                        post.category,
                        post.title,
                        post.content,
                        post.viewCount,
                        post.bookmarkCount,
                        post.isSolved,
                        // 북마크 여부: 로그인 유저가 해당 글을 북마크 했는지 서브쿼리 또는 Join으로 확인
                        ExpressionUtils.as(
                                new com.querydsl.core.types.dsl.CaseBuilder()
                                        .when(isBookmarkedCondition(postId, loginUserId))
                                        .then(true)
                                        .otherwise(false),
                                "isBookmarked"
                        ),
                        post.createdAt,
                        post.lastModifiedAt
                ))
                .from(post)
                .leftJoin(userProfile).on(post.userId.eq(userProfile.userId))
                .where(post.id.eq(postId))
                .fetchOne());
    }

    // 댓글 목록 조회 (작성자 Join)
    @Override
    public List<CommentResponse> findCommentsByPostId(Long postId) {
        return queryFactory
                .select(Projections.fields(CommentResponse.class,
                        postComment.id,
                        postComment.post.id.as("postId"),
                        postComment.userId,
                        userProfile.name.as("writerName"),
                        userProfile.email.as("writerEmail"),
                        postComment.content,
                        postComment.isAccepted,
                        postComment.parent.id.as("parentId"),
                        postComment.createdAt,
                        postComment.lastModifiedAt
                ))
                .from(postComment)
                .leftJoin(userProfile).on(postComment.userId.eq(userProfile.userId))
                .where(postComment.post.id.eq(postId))
                .orderBy(postComment.createdAt.asc()) // 작성순 정렬
                .fetch();
    }

    @Override
    public Page<MyPostResponse> findMyPosts(Long userId, PostCategory category, Pageable pageable) {
        List<PostEntity> posts = queryFactory
                .selectFrom(post)
                .where(
                        post.userId.eq(userId),
                        categoryEq(category)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(post.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .where(post.userId.eq(userId), categoryEq(category))
                .fetchOne();

        if (total == null) total = 0L;

        List<MyPostResponse> responses = posts.stream()
                .map(MyPostResponse::from)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, total);
    }

    @Override
    public Page<MyBookmarkPostResponse> findMyBookmarkedPosts(Long userId, PostCategory category, Pageable pageable) {
        List<MyBookmarkPostResponse> content = queryFactory
                .select(Projections.fields(MyBookmarkPostResponse.class,
                        post.id,
                        post.title,
                        post.content,
                        post.category,
                        post.createdAt,
                        userProfile.name.as("writerName") // 유저 테이블과 조인하여 이름 바로 매핑
                ))
                .from(post)
                .join(postBookmark).on(post.id.eq(postBookmark.post.id))
                .leftJoin(userProfile).on(post.userId.eq(userProfile.userId)) // 작성자 정보 조인 (Post.userId = UserProfile.userId)
                .where(
                        postBookmark.userId.eq(userId),
                        categoryEq(category)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(postBookmark.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(post.count())
                .from(post)
                .join(postBookmark).on(post.id.eq(postBookmark.post.id))
                .where(postBookmark.userId.eq(userId), categoryEq(category))
                .fetchOne();

        if (total == null) total = 0L;

        return new PageImpl<>(content, pageable, total);
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

    private BooleanExpression isBookmarkedCondition(Long postId, Long loginUserId) {
        if (loginUserId == null) {
            return Expressions.asBoolean(false).isTrue(); // 로그인 안 했으면 false
        }
        // SubQuery: select 1 from bookmark where post_id = ? and user_id = ?
        return JPAExpressions.selectOne()
                .from(postBookmark)
                .where(postBookmark.post.id.eq(postId)
                        .and(postBookmark.userId.eq(loginUserId)))
                .exists();
    }
}