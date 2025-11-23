package com.example.communityservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.commonmodule.exception.CommonErrorCode;
import com.example.communityservice.client.ChatServiceClient;
import com.example.communityservice.dto.request.PostCreateRequest;
import com.example.communityservice.dto.response.PostResponse;
import com.example.communityservice.entity.*;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import com.example.communityservice.repository.PostBookmarkRepository;
import com.example.communityservice.repository.PostCommentRepository;
import com.example.communityservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final ChatServiceClient chatServiceClient;

    @Transactional
    public Long createPost(Long userId, PostCreateRequest request) {
        PostEntity post = PostEntity.builder()
                .userId(userId)
                .category(request.getCategory())
                .title(request.getTitle())
                .content(request.getContent())
                .build();

        if (request.getTags() != null) {
            request.getTags().forEach(tagName ->
                    post.addTag(new PostTagEntity(post, tagName))
            );
        }

        return postRepository.save(post).getId();
    }

    public Page<PostResponse> getPosts(PostCategory category, String keyword, Boolean isSolved, Pageable pageable) {
        return postRepository.searchPosts(category, keyword, isSolved, pageable);
    }

    @Transactional
    public PostResponse getPostDetail(Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        post.increaseViewCount();

        return PostResponse.from(post);
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

    // 북마크 토글 (좋아요 대체)
    @Transactional
    public void toggleBookmark(Long userId, Long postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));

        postBookmarkRepository.findByPostAndUserId(post, userId).ifPresentOrElse(
                bookmark -> { // 이미 북마크가 있으면 삭제 (취소)
                    postBookmarkRepository.delete(bookmark);
                    post.decreaseBookmarkCount();
                },
                () -> { // 없으면 추가
                    postBookmarkRepository.save(new PostBookmarkEntity(post, userId));
                    post.increaseBookmarkCount();
                }
        );
    }
}