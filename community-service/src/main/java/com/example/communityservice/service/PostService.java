package com.example.communityservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.commonmodule.exception.CommonErrorCode;
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
import com.example.communityservice.repository.PostUserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.communityservice.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final PostUserProfileRepository userProfileRepository;
    private final ChatServiceClient chatServiceClient;

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

    @Transactional
    public void updatePost(Long userId, Long postId, PostUpdateRequest request) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }

        post.update(request.getTitle(), request.getContent());
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }

        postRepository.delete(post);
    }

    public Page<PostResponse> getPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
        return postRepository.searchPosts(category, keyword, isSolved, pageable);
    }

    // 상세 조회
    @Transactional
    public PostDetailResponse getPostDetail(Long postId, Long currentUserId) {
        // 1. 게시글 상세 정보 조회 (작성자 정보 + 북마크 여부 포함됨)
        PostDetailResponse response = postRepository.findPostDetailById(postId, currentUserId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        // 2. 조회수 증가 (Entity 조회 없이 Update 쿼리만 실행하여 성능 향상 권장)
        updateViewCount(postId);

        // 3. 댓글 목록 조회 (작성자 정보 포함됨)
        List<CommentResponse> comments = postRepository.findCommentsByPostId(postId);

        // 4. 댓글 계층 구조 조립 (메모리 연산)
        response.setComments(convertToCommentHierarchy(comments));

        return response;
    }

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
    }

    @Transactional
    public void updateComment(Long userId, Long postId, Long commentId, CommentRequest request) {
        // postId는 URL 유효성 검증용으로 받을 수 있으나, 여기서는 commentId로 조회 후 권한만 확인
        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));

        // URL의 postId와 댓글의 실제 postId가 일치하는지 검증
        if (!comment.getPost().getId().equals(postId)) {
            throw new BusinessException(INVALID_INPUT_VALUE);
        }

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(NOT_COMMENT_OWNER);
        }

        comment.updateContent(request.getContent());
    }

    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));

        // URL의 postId와 댓글의 실제 postId가 일치하는지 검증
        if (!comment.getPost().getId().equals(postId)) {
            throw new BusinessException(INVALID_INPUT_VALUE);
        }

        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(NOT_COMMENT_OWNER);
        }

        // 자식 댓글이 있는 경우 Cascade 설정에 따라 함께 삭제됨
        commentRepository.delete(comment);
    }

    @Transactional
    public void adoptAnswer(Long userId, Long postId, Long commentId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }
        if (post.getCategory() != PostCategory.QNA) {
            throw new BusinessException(NOT_QNA_CATEGORY);
        }

        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(COMMENT_NOT_FOUND));

        comment.accept();
        post.markAsSolved();
    }

    public void applyTeam(Long applicantId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (post.getCategory() != PostCategory.RECRUIT) {
            throw new BusinessException(NOT_RECRUIT_CATEGORY);
        }
        if (post.getRecruitmentStatus() == RecruitmentStatus.CLOSED) {
            throw new BusinessException(RECRUITMENT_CLOSED);
        }

        String message = String.format("안녕하세요, '%s' 모집 글을 보고 지원합니다.", post.getTitle());
        try {
            chatServiceClient.sendMessageInternal(applicantId, post.getUserId(), message);
        } catch (Exception e) {
            log.error("메시지 전송 실패: {}", e.getMessage());
            throw new BusinessException(MESSAGE_SEND_FAILED);
        }
    }

    @Transactional
    public void updateRecruitmentStatus(Long userId, Long postId, RecruitmentStatus status) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(POST_NOT_FOUND));

        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(NOT_POST_OWNER);
        }
        if (post.getCategory() != PostCategory.RECRUIT) {
            throw new BusinessException(NOT_RECRUIT_CATEGORY);
        }

        post.updateRecruitmentStatus(status);
    }

    @Transactional
    public void toggleBookmark(Long userId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        postBookmarkRepository.findByPostAndUserId(post, userId).ifPresentOrElse(
                bookmark -> {
                    postBookmarkRepository.delete(bookmark);
                    post.decreaseBookmarkCount();
                },
                () -> {
                    postBookmarkRepository.save(new PostBookmarkEntity(post, userId));
                    post.increaseBookmarkCount();
                }
        );
    }

    public Page<MyPostResponse> getMyPosts(Long userId, PostCategory category, Pageable pageable) {
        return postRepository.findMyPosts(userId, category, pageable);
    }

    // 내가 북마크한 글 조회 (원 글 작성자 이름 매핑 추가)
    public Page<MyBookmarkPostResponse> getMyBookmarkedPosts(Long userId, PostCategory category, Pageable pageable) {
        return postRepository.findMyBookmarkedPosts(userId, category, pageable);
    }

    // ==========================================
    // Private Helpers
    // ==========================================

    // 댓글 계층 구조 변환 (DTO 기반으로 동작)
    private List<CommentResponse> convertToCommentHierarchy(List<CommentResponse> comments) {
        Map<Long, CommentResponse> map = new HashMap<>();
        List<CommentResponse> roots = new ArrayList<>();

        // 매핑
        for (CommentResponse dto : comments) {
            map.put(dto.getId(), dto);
        }

        // 조립
        for (CommentResponse dto : comments) {
            if (dto.getParentId() != null) {
                CommentResponse parent = map.get(dto.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                }
            } else {
                roots.add(dto);
            }
        }
        return roots;
    }

    // 조회수 증가용 별도 메서드
    private void updateViewCount(Long postId) {
        postRepository.findById(postId).ifPresent(PostEntity::increaseViewCount);
    }

}