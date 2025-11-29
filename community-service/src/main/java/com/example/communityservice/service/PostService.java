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
@Transactional(readOnly = true)
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
    private static final String POST_INFO_KEY_PREFIX = "post:info:"; // 게시글 본문 (변경 적음)
    private static final String POST_STATS_KEY_PREFIX = "post:stats:"; // 통계 (조회수, 북마크)
    private static final String VIEW_BATCH_KEY = "post:views";       // 배치용 조회수 누적

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

    // 2. 게시글 수정 (캐시 삭제)
    @Transactional
    public void updatePost(Long userId, Long postId, PostUpdateRequest request) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }

        post.update(request.getTitle(), request.getContent());

        // 본문 내용이 변경되었으므로 캐시 삭제 (Eviction)
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
    }

    // 3. 게시글 삭제 (캐시 및 통계 삭제)
    @Transactional
    public void deletePost(Long userId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }

        postRepository.delete(post);

        // 관련 Redis 데이터 모두 삭제
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
        redisTemplate.delete(POST_STATS_KEY_PREFIX + postId);
    }

    // 4. 게시글 목록 조회 (검색/필터)
    public Page<PostResponse> getPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
        return postRepository.searchPosts(category, keyword, isSolved, pageable);
    }

    // 5. 게시글 상세 조회 (Split & Merge 전략)
    @Transactional
    public PostDetailResponse getPostDetail(Long postId, Long currentUserId) {

        // A. 조회수 증가 (Redis: 실시간용 + 배치용)
        redisTemplate.opsForHash().increment(POST_STATS_KEY_PREFIX + postId, "viewCount", 1L);
        redisTemplate.opsForHash().increment(VIEW_BATCH_KEY, String.valueOf(postId), 1L);

        // B. 게시글 본문 조회 (Cache Hit 시 DB 조회 안 함)
        PostDetailResponse response = getCachedPostBaseInfo(postId);

        // C. 댓글 목록 조회 (항상 DB에서 조회 - 실시간성 보장)
        List<CommentResponse> comments = postRepository.findCommentsByPostId(postId);
        response.setComments(convertToCommentHierarchy(comments));

        // D. 통계 데이터 병합 (조회수, 북마크 수 Redis 조회)
        mergeDynamicStats(postId, response);

        // E. 개인화 정보(북마크 여부) 확인 (DB 조회)
        if (currentUserId != null) {
            PostEntity proxyPost = postRepository.getReferenceById(postId);
            boolean isBookmarked = postBookmarkRepository.existsByPostAndUserId(proxyPost, currentUserId);
            response.setBookmarked(isBookmarked);
        }

        return response;
    }

    // 6. 댓글 작성 (DB 저장 + 통계 업데이트)
    @Transactional
    public void createComment(Long userId, Long postId, CommentRequest request) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

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

        // DB 필드 업데이트 (댓글 내용은 캐싱하지 않으므로 본문 캐시 삭제 불필요)
        post.increaseCommentCount();
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

        // DB 필드 감소
        PostEntity post = comment.getPost();
        post.decreaseCommentCount();
    }

    // 9. 답변 채택 (QnA) - 본문 변경으로 간주하여 캐시 삭제
    @Transactional
    public void adoptAnswer(Long userId, Long postId, Long commentId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) throw new BusinessException(NOT_POST_OWNER);
        if (post.getCategory() != PostCategory.QNA) throw new BusinessException(NOT_QNA_CATEGORY);

        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));

        comment.accept();
        post.markAsSolved();

        // 해결 여부(isSolved)는 게시글 정보에 포함되므로 캐시 삭제
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
    }

    // 10. 북마크 토글 (DB + Redis 통계 동시 업데이트)
    @Transactional
    public void toggleBookmark(Long userId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        String statsKey = POST_STATS_KEY_PREFIX + postId;

        postBookmarkRepository.findByPostAndUserId(post, userId).ifPresentOrElse(
                bookmark -> {
                    // 취소
                    postBookmarkRepository.delete(bookmark);
                    post.decreaseBookmarkCount();
                    redisTemplate.opsForHash().increment(statsKey, "bookmarkCount", -1L);
                },
                () -> {
                    // 등록
                    postBookmarkRepository.save(new PostBookmarkEntity(post, userId));
                    post.increaseBookmarkCount();
                    redisTemplate.opsForHash().increment(statsKey, "bookmarkCount", 1L);
                }
        );
    }

    // 11. 팀원 모집 지원
    public void applyTeam(Long applicantId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

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

    // 12. 모집 상태 변경 (캐시 삭제)
    @Transactional
    public void updateRecruitmentStatus(Long userId, Long postId, RecruitmentStatus status) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));
        if (!post.getUserId().equals(userId)) throw new BusinessException(NOT_POST_OWNER);
        if (post.getCategory() != PostCategory.RECRUIT) throw new BusinessException(NOT_RECRUIT_CATEGORY);

        post.updateRecruitmentStatus(status);

        // 상태 변경은 게시글 본문 정보이므로 캐시 삭제
        redisTemplate.delete(POST_INFO_KEY_PREFIX + postId);
    }

    // 13. 마이페이지 관련 조회
    public Page<MyPostResponse> getMyPosts(Long userId, PostCategory category, Pageable pageable) {
        return postRepository.findMyPosts(userId, category, pageable);
    }

    public Page<MyBookmarkPostResponse> getMyBookmarkedPosts(Long userId, PostCategory category, Pageable pageable) {
        return postRepository.findMyBookmarkedPosts(userId, category, pageable);
    }

    // ==========================================
    // Private Helpers (Caching Logic)
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

        // Cache Miss -> DB 조회 (댓글 제외)
        // null을 파라미터로 넘겨 북마크 여부 false 처리 (개인화 데이터 제외)
        PostDetailResponse response = postRepository.findPostDetailById(postId, null)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        // 캐시 객체에는 댓글 리스트를 빈 상태로 저장 (메모리 절약)
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
        List<Object> stats = redisTemplate.opsForHash().multiGet(statsKey, Arrays.asList("viewCount", "bookmarkCount"));

        Object viewCount = stats.get(0);
        Object bookmarkCount = stats.get(1);

        // Redis Miss 시 DB 값으로 초기화
        if (viewCount == null || bookmarkCount == null) {
            redisTemplate.opsForHash().putIfAbsent(statsKey, "viewCount", String.valueOf(response.getViewCount()));
            redisTemplate.opsForHash().putIfAbsent(statsKey, "bookmarkCount", String.valueOf(response.getBookmarkCount()));
        } else {
            if (viewCount != null) response.setViewCount(Long.parseLong(viewCount.toString()));
            if (bookmarkCount != null) response.setBookmarkCount(Long.parseLong(bookmarkCount.toString()));
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