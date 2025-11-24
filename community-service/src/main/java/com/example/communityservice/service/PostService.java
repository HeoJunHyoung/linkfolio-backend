package com.example.communityservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.commonmodule.exception.CommonErrorCode;
import com.example.communityservice.client.ChatServiceClient;
import com.example.communityservice.dto.request.PostCreateRequest;
import com.example.communityservice.dto.response.CommentResponse;
import com.example.communityservice.dto.response.PostDetailResponse;
import com.example.communityservice.dto.response.PostResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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

    public Page<PostResponse> getPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
        Page<PostResponse> postResponses = postRepository.searchPosts(category, keyword, isSolved, pageable);
        mapWriterInfo(postResponses.getContent());
        return postResponses;
    }

    // 상세 조회 로직 구현 (댓글 계층 구조, 사용자 정보 매핑, 북마크 여부)
    @Transactional
    public PostDetailResponse getPostDetail(Long postId, Long currentUserId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        post.increaseViewCount();

        // 1. 기본 Post 정보 변환
        PostDetailResponse response = PostDetailResponse.from(post);

        // 2. 북마크 여부 확인
        if (currentUserId != null) {
            boolean isBookmarked = postBookmarkRepository.existsByPostAndUserId(post, currentUserId);
            response.setBookmarked(isBookmarked);
        }

        // 3. 댓글 조회 및 계층 구조 조립
        List<PostCommentEntity> comments = commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId);
        List<CommentResponse> commentResponses = convertToCommentHierarchy(comments);
        response.setComments(commentResponses);

        // 4. 게시글 작성자 및 댓글 작성자 정보 일괄 매핑
        Set<Long> userIds = new HashSet<>();
        userIds.add(post.getUserId()); // 게시글 작성자
        comments.forEach(c -> userIds.add(c.getUserId())); // 댓글 작성자들

        Map<Long, PostUserProfileEntity> userMap = userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(PostUserProfileEntity::getUserId, u -> u));

        // 게시글 작성자 정보 세팅
        setWriterInfo(response, userMap.get(post.getUserId()));

        // 댓글 작성자 정보 세팅 (재귀적으로 처리하지 않고, Flat한 리스트에서 처리 후 조립했으므로 참조를 통해 반영됨)
        // 위 convertToCommentHierarchy 내부에서 DTO를 만들었으므로, 여기서 순회하며 값을 채워줍니다.
        updateCommentWriterInfo(response.getComments(), userMap);

        return response;
    }

    // 댓글 계층 구조 변환 (Parent-Child)
    private List<CommentResponse> convertToCommentHierarchy(List<PostCommentEntity> entities) {
        Map<Long, CommentResponse> map = new HashMap<>();
        List<CommentResponse> roots = new ArrayList<>();

        // 1. DTO 변환 및 Map 저장
        for (PostCommentEntity entity : entities) {
            CommentResponse dto = CommentResponse.from(entity);
            map.put(entity.getId(), dto);
        }

        // 2. 계층 구조 조립
        for (PostCommentEntity entity : entities) {
            CommentResponse currentDto = map.get(entity.getId());
            if (entity.getParent() != null) {
                CommentResponse parentDto = map.get(entity.getParent().getId());
                if (parentDto != null) { // 부모가 존재하면 자식 리스트에 추가
                    parentDto.getChildren().add(currentDto);
                }
            } else {
                // 부모가 없으면 최상위 댓글
                roots.add(currentDto);
            }
        }
        return roots;
    }

    // 댓글 리스트(대댓글 포함)에 작성자 정보 매핑
    private void updateCommentWriterInfo(List<CommentResponse> comments, Map<Long, PostUserProfileEntity> userMap) {
        for (CommentResponse comment : comments) {
            PostUserProfileEntity user = userMap.get(comment.getUserId());
            if (user != null) {
                comment.setWriterName(user.getName());
                comment.setWriterEmail(user.getEmail());
            } else {
                comment.setWriterName("알 수 없음");
            }

            // 대댓글에 대해서도 재귀 호출
            if (comment.getChildren() != null && !comment.getChildren().isEmpty()) {
                updateCommentWriterInfo(comment.getChildren(), userMap);
            }
        }
    }

    // 게시글 작성자 정보 세팅 헬퍼
    private void setWriterInfo(PostDetailResponse response, PostUserProfileEntity user) {
        if (user != null) {
            response.setWriterName(user.getName());
            response.setWriterEmail(user.getEmail());
        } else {
            response.setWriterName("알 수 없음");
        }
    }

    // 목록 조회용 작성자 정보 매핑
    private void mapWriterInfo(List<PostResponse> posts) {
        Set<Long> userIds = posts.stream().map(PostResponse::getUserId).collect(Collectors.toSet());
        Map<Long, PostUserProfileEntity> userMap = userProfileRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(PostUserProfileEntity::getUserId, u -> u));

        posts.forEach(post -> {
            PostUserProfileEntity user = userMap.get(post.getUserId());
            if (user != null) {
                post.setWriterName(user.getName());
                post.setWriterEmail(user.getEmail());
            } else {
                post.setWriterName("알 수 없음");
            }
        });
    }

    @Transactional
    public void adoptAnswer(Long userId, Long postId, Long commentId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (!post.getUserId().equals(userId)) {
            throw new RuntimeException("작성자만 채택할 수 있습니다.");
        }
        if (post.getCategory() != PostCategory.QNA) {
            throw new RuntimeException("QnA 게시글이 아닙니다.");
        }

        PostCommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("댓글을 찾을 수 없습니다."));

        comment.accept();
        post.markAsSolved();
    }

    public void applyTeam(Long applicantId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        if (post.getCategory() != PostCategory.RECRUIT) {
            throw new RuntimeException("팀원 모집 게시글이 아닙니다.");
        }
        if (post.getRecruitmentStatus() == RecruitmentStatus.CLOSED) {
            throw new RuntimeException("이미 마감된 모집입니다.");
        }

        String message = String.format("안녕하세요, '%s' 모집 글을 보고 지원합니다.", post.getTitle());
        try {
            chatServiceClient.sendMessageInternal(applicantId, post.getUserId(), message);
        } catch (Exception e) {
            log.error("메시지 전송 실패: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
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
}