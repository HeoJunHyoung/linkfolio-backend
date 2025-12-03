package com.example.communityservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.communityservice.client.ChatServiceClient;
import com.example.communityservice.dto.request.CommentRequest;
import com.example.communityservice.dto.request.PostCreateRequest;
import com.example.communityservice.dto.request.PostUpdateRequest;
import com.example.communityservice.dto.response.*;
import com.example.communityservice.entity.*;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import com.example.communityservice.repository.PostBookmarkRepository;
import com.example.communityservice.repository.PostCommentRepository;
import com.example.communityservice.repository.PostRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.example.communityservice.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final ChatServiceClient chatServiceClient;

    // Redis & Utils
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis Keys
    private static final String POST_INFO_KEY_PREFIX = "post:info:";
    private static final String POST_STATS_KEY_PREFIX = "post:stats:";

    // Batch Keys
    private static final String VIEW_BATCH_KEY = "post:views";
    private static final String BOOKMARK_BATCH_KEY = "post:bookmarks:delta";
    private static final String COMMENT_BATCH_KEY = "post:comments:delta";

    // 1. 게시글 생성
    @Transactional
    public Long createPost(Long userId, PostCreateRequest request) {
        PostEntity post = PostEntity.builder()
                .userId(userId)
                .category(request.getCategory())
                .title(request.getTitle())
                .content(request.getContent())
                .build();
        return postRepository.save(post).getId();
    }

    // 2. 게시글 수정
    @Transactional
    public void updatePost(Long userId, Long postId, PostUpdateRequest request) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }

        post.update(request.getTitle(), request.getContent());
        // 캐시 무효화 (DB 커밋 전/후 언제든 상관없으나, 트랜잭션 안에서 수행)
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
    }

    // 3. 게시글 삭제
    @Transactional
    public void deletePost(Long userId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }

        postRepository.delete(post);
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
        redisTemplate.delete(POST_STATS_KEY_PREFIX + postId);
    }

    // 4. 게시글 목록 조회
    @Transactional(readOnly = true)
    public Page<PostResponse> getPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
        return postRepository.searchPosts(category, keyword, isSolved, pageable);
    }

    // 5. 게시글 상세 조회
    public PostDetailResponse getPostDetail(Long postId, Long currentUserId) {

        // A. 조회수 증가 (Redis) - DB 연결 필요 없음
        redisTemplate.opsForHash().increment(POST_STATS_KEY_PREFIX + postId, "viewCount", 1L);
        redisTemplate.opsForHash().increment(VIEW_BATCH_KEY, String.valueOf(postId), 1L);

        // B. 게시글 본문 조회
        // 내부적으로 Repository 호출 시 순간적으로 커넥션을 얻고 즉시 반환함
        PostDetailResponse response = getCachedPostBaseInfo(postId);

        // C. 댓글 목록 조회 (DB)
        // 이 시점에 커넥션을 얻고, 쿼리 실행 후 즉시 반환
        List<CommentResponse> comments = postRepository.findCommentsByPostId(postId);
        response.setComments(convertToCommentHierarchy(comments));

        // D. 통계 데이터 병합 (Redis) - DB 연결 필요 없음
        mergeDynamicStats(postId, response);

        // E. 개인화 정보 확인 (DB)
        // 캐시된 데이터(response)는 공통 정보이므로, 내 북마크 여부는 별도로 확인해야 함.
        if (currentUserId != null) {
            // getReferenceById는 프록시만 가져오므로 DB 조회 발생 안 함
            PostEntity proxyPost = postRepository.getReferenceById(postId);
            // exists 쿼리는 매우 가볍고, 인덱스를 타므로 성능 영향 미미함.
            // 핵심은 이 쿼리 수행 시간 동안만 커넥션을 점유한다는 것임.
            boolean isBookmarked = postBookmarkRepository.existsByPostAndUserId(proxyPost, currentUserId);
            response.setBookmarked(isBookmarked);
        }

        return response;
    }

    // 6. 댓글 작성
    @Transactional
    public void createComment(Long userId, Long postId, CommentRequest request) {
        PostEntity post = postRepository.getReferenceById(postId); // Proxy

        PostCommentEntity parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));
        }

        PostCommentEntity comment = PostCommentEntity.builder()
                .post(post)
                .userId(userId)
                .content(request.getContent())
                .parent(parent)
                .build();

        commentRepository.save(comment);

        redisTemplate.opsForHash().increment(POST_STATS_KEY_PREFIX + postId, "commentCount", 1L);
        redisTemplate.opsForHash().increment(COMMENT_BATCH_KEY, String.valueOf(postId), 1L);
    }

    // 7. 댓글 수정
    @Transactional
    public void updateComment(Long userId, Long postId, Long commentId, CommentRequest request) {
        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));

        if (!comment.getPost().getId().equals(postId)) throw new BusinessException(INVALID_INPUT_VALUE);
        if (!comment.getUserId().equals(userId)) throw new BusinessException(NOT_COMMENT_OWNER);

        comment.updateContent(request.getContent());
    }

    // 8. 댓글 삭제
    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));
        if (!comment.getPost().getId().equals(postId)) throw new BusinessException(INVALID_INPUT_VALUE);
        if (!comment.getUserId().equals(userId)) throw new BusinessException(NOT_COMMENT_OWNER);

        commentRepository.delete(comment);

        redisTemplate.opsForHash().increment(POST_STATS_KEY_PREFIX + postId, "commentCount", -1L);
        redisTemplate.opsForHash().increment(COMMENT_BATCH_KEY, String.valueOf(postId), -1L);
    }

    // 9. 답변 채택
    @Transactional
    public void adoptAnswer(Long userId, Long postId, Long commentId) {
        PostEntity post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        if (!post.getUserId().equals(userId)) throw new BusinessException(NOT_POST_OWNER);
        if (post.getCategory() != PostCategory.QNA) throw new BusinessException(NOT_QNA_CATEGORY);

        PostCommentEntity comment = commentRepository.findById(commentId).orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));
        comment.accept();
        post.markAsSolved();
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
    }

    // 10. 북마크 토글
    @Transactional
    public void toggleBookmark(Long userId, Long postId) {
        PostEntity post = postRepository.getReferenceById(postId);
        String statsKey = POST_STATS_KEY_PREFIX + postId;

        postBookmarkRepository.findByPostAndUserId(post, userId).ifPresentOrElse(
                bookmark -> {
                    // 취소
                    postBookmarkRepository.delete(bookmark);
                    redisTemplate.opsForHash().increment(statsKey, "bookmarkCount", -1L);
                    redisTemplate.opsForHash().increment(BOOKMARK_BATCH_KEY, String.valueOf(postId), -1L);
                },
                () -> {
                    // 등록
                    postBookmarkRepository.save(new PostBookmarkEntity(post, userId));
                    redisTemplate.opsForHash().increment(statsKey, "bookmarkCount", 1L);
                    redisTemplate.opsForHash().increment(BOOKMARK_BATCH_KEY, String.valueOf(postId), 1L);
                }
        );
    }

    // 11. 팀원 모집 지원 (Read/Feign -> DB Write 없음)
    @Transactional(readOnly = true)
    public void applyTeam(Long applicantId, Long postId) {
        PostEntity post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        if (post.getCategory() != PostCategory.RECRUIT) throw new BusinessException(NOT_RECRUIT_CATEGORY);
        if (post.getRecruitmentStatus() == RecruitmentStatus.CLOSED) throw new BusinessException(RECRUITMENT_CLOSED);

        String message = String.format("안녕하세요, '%s' 모집 글을 보고 지원합니다.", post.getTitle());
        try {
            chatServiceClient.sendMessageInternal(applicantId, post.getUserId(), message);
        } catch (Exception e) {
            log.error("메시지 전송 실패", e);
            throw new BusinessException(MESSAGE_SEND_FAILED);
        }
    }

    // 12. 모집 상태 변경
    @Transactional
    public void updateRecruitmentStatus(Long userId, Long postId, RecruitmentStatus status) {
        PostEntity post = postRepository.findById(postId).orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        if (!post.getUserId().equals(userId)) throw new BusinessException(NOT_POST_OWNER);
        if (post.getCategory() != PostCategory.RECRUIT) throw new BusinessException(NOT_RECRUIT_CATEGORY);
        post.updateRecruitmentStatus(status);
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
    }

    // 13. 마이페이지 관련 조회
    @Transactional(readOnly = true)
    public Page<MyPostResponse> getMyPosts(Long userId, PostCategory category, Pageable pageable) {
        return postRepository.findMyPosts(userId, category, pageable);
    }

    @Transactional(readOnly = true)
    public Page<MyBookmarkPostResponse> getMyBookmarkedPosts(Long userId, PostCategory category, Pageable pageable) {
        return postRepository.findMyBookmarkedPosts(userId, category, pageable);
    }

    // ==========================================
    // Private Helpers
    // ==========================================

    private PostDetailResponse getCachedPostBaseInfo(Long postId) {
        String cacheKey = POST_INFO_KEY_PREFIX + postId;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        if (StringUtils.hasText(cachedJson)) {
            try {
                return objectMapper.readValue(cachedJson, PostDetailResponse.class);
            } catch (JsonProcessingException e) {
                log.error("JSON Parsing Error", e);
            }
        }

        // Cache Miss -> DB 조회
        // Repository 메서드 호출 시점에만 DB 커넥션 사용
        PostDetailResponse response = postRepository.findPostDetailById(postId, null)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        response.setComments(new ArrayList<>());

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Redis Set Error", e);
        }
        return response;
    }

    private void mergeDynamicStats(Long postId, PostDetailResponse response) {
        String statsKey = POST_STATS_KEY_PREFIX + postId;
        List<Object> stats = redisTemplate.opsForHash().multiGet(statsKey, Arrays.asList("viewCount", "bookmarkCount", "commentCount"));

        Object viewCount = stats.get(0);
        Object bookmarkCount = stats.get(1);
        Object commentCount = stats.get(2);

        if (viewCount == null || bookmarkCount == null || commentCount == null) {
            redisTemplate.opsForHash().putIfAbsent(statsKey, "viewCount", String.valueOf(response.getViewCount()));
            redisTemplate.opsForHash().putIfAbsent(statsKey, "bookmarkCount", String.valueOf(response.getBookmarkCount()));

            String dbCommentCount = response.getCommentCount() != null ? String.valueOf(response.getCommentCount()) : "0";
            redisTemplate.opsForHash().putIfAbsent(statsKey, "commentCount", dbCommentCount);
        } else {
            response.setViewCount(Long.parseLong(viewCount.toString()));
            response.setBookmarkCount(Long.parseLong(bookmarkCount.toString()));
            response.setCommentCount(Long.parseLong(commentCount.toString()));
        }
    }

    private List<CommentResponse> convertToCommentHierarchy(List<CommentResponse> comments) {
        Map<Long, CommentResponse> map = new HashMap<>();
        List<CommentResponse> roots = new ArrayList<>();
        for (CommentResponse dto : comments) map.put(dto.getId(), dto);
        for (CommentResponse dto : comments) {
            if (dto.getParentId() != null) {
                CommentResponse parent = map.get(dto.getParentId());
                if (parent != null) parent.getChildren().add(dto);
            } else {
                roots.add(dto);
            }
        }
        return roots;
    }
}