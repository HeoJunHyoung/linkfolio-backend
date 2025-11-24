//package com.example.communityservice.repository;
//
//import com.example.communityservice.dto.response.PostResponse;
//import com.example.communityservice.entity.PostEntity;
//import com.example.communityservice.entity.QCommunityUserProfileEntity;
//import com.example.communityservice.entity.QPostEntity;
//import com.example.communityservice.entity.enumerate.PostCategory;
//import com.querydsl.core.types.Projections;
//import com.querydsl.core.types.dsl.BooleanExpression;
//import com.querydsl.jpa.impl.JPAQuery;
//import com.querydsl.jpa.impl.JPAQueryFactory;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.Pageable;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@RequiredArgsConstructor
//public class PostRepositoryImpl implements PostRepositoryCustom {
//
//    private final JPAQueryFactory queryFactory;
//    private final QPostEntity post = QPostEntity.postEntity;
//    // 빌드 시 생성될 Q클래스 (CommunityUserProfileEntity 추가 후 빌드 필요)
//    private final QCommunityUserProfileEntity userProfile = QCommunityUserProfileEntity.communityUserProfileEntity;
//
//    @Override
//    public Page<PostResponse> searchPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
//
//        // 1. 데이터 조회 (Fetch Join or Projections)
//        // 여기서는 PostEntity를 가져오면서 UserProfile은 DTO 변환 시점에 매핑하기 위해 Fetch Join 대신
//        // 필요한 데이터를 한 번에 가져오거나, 배치 사이즈를 활용할 수 있습니다.
//        // QueryDSL Projections를 사용하여 DTO로 바로 변환하는 것이 성능상 유리합니다.
//
//        List<PostEntity> posts = queryFactory
//                .selectFrom(post)
//                .leftJoin(userProfile).on(post.userId.eq(userProfile.userId)) // userId로 조인
//                .where(
//                        categoryEq(category),
//                        keywordContains(keyword),
//                        isSolvedEq(isSolved)
//                )
//                .offset(pageable.getOffset())
//                .limit(pageable.getPageSize())
//                .orderBy(post.createdAt.desc())
//                .fetch();
//
//        // 2. Count Query
//        Long total = queryFactory
//                .select(post.count())
//                .from(post)
//                .where(
//                        categoryEq(category),
//                        keywordContains(keyword),
//                        isSolvedEq(isSolved)
//                )
//                .fetchOne();
//
//        if (total == null) total = 0L;
//
//        // 3. Entity -> DTO 변환 (이 과정에서 UserProfile 정보를 어떻게 넣을지 결정)
//        // 간단하게 구현하기 위해, PostResponse.from() 내부에서 userProfile 정보를 채우는 방식보다는
//        // 위 쿼리에서 Tuple로 가져오거나, PostResponse 생성자에 필요한 필드를 넘기는 것이 좋으나
//        // 현재 구조 유지를 위해 Service 계층에서 조합하거나,
//        // 여기서는 일단 PostEntity만 반환하고 Service에서 mapUserNames를 수행하는 것이 깔끔할 수 있습니다.
//        // 하지만 성능 최적화를 위해 여기서 DTO 변환 로직을 수행합니다.
//
//        List<PostResponse> responses = posts.stream()
//                .map(p -> {
//                    // N+1 방지를 위해 영속성 컨텍스트나 배치 조회 활용 권장.
//                    // 여기서는 간단히 구현하고, 실제로는 UserProfileRepository.findAllById()로 작성자 정보를 한 번에 가져와 매핑하는 것이 정석입니다.
//                    return PostResponse.from(p);
//                })
//                .collect(Collectors.toList());
//
//        // *참고: 위 방식은 작성자 이름이 PostResponse에 안 들어갑니다.
//        // 작성자 이름을 넣으려면 Service 계층에서 `mapWriterInfo`를 호출해주는 것이 좋습니다.
//
//        return new PageImpl<>(responses, pageable, total);
//    }
//
//    private BooleanExpression categoryEq(PostCategory category) {
//        return category != null ? post.category.eq(category) : null;
//    }
//
//    private BooleanExpression keywordContains(String keyword) {
//        if (keyword == null || keyword.isBlank()) return null;
//        return post.title.containsIgnoreCase(keyword)
//                .or(post.content.containsIgnoreCase(keyword));
//    }
//
//    private BooleanExpression isSolvedEq(Boolean isSolved) {
//        return isSolved != null ? post.isSolved.eq(isSolved) : null;
//    }
//}