package com.example.communityservice.controller;

import com.example.commonmodule.dto.security.AuthUser;
import com.example.communityservice.dto.request.CommentRequest;
import com.example.communityservice.dto.request.PostCreateRequest;
import com.example.communityservice.dto.request.PostUpdateRequest;
import com.example.communityservice.dto.request.RecruitmentStatusRequest;
import com.example.communityservice.dto.response.PostDetailResponse;
import com.example.communityservice.dto.response.PostResponse;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Community API", description = "커뮤니티(게시판) API")
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 작성", description = "QnA, 정보공유, 팀원모집 게시글을 작성합니다.")
    @PostMapping("/posts")
    public ResponseEntity<Long> createPost(@AuthenticationPrincipal AuthUser authUser,
                                           @RequestBody @Valid PostCreateRequest request) {
        return ResponseEntity.ok(postService.createPost(authUser.getUserId(), request));
    }

    @Operation(summary = "게시글 수정", description = "제목과 내용을 수정합니다.")
    @PutMapping("/posts/{postId}")
    public ResponseEntity<Void> updatePost(@AuthenticationPrincipal AuthUser authUser,
                                           @PathVariable Long postId,
                                           @RequestBody @Valid PostUpdateRequest request) {
        postService.updatePost(authUser.getUserId(), postId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthUser authUser,
                                           @PathVariable Long postId) {
        postService.deletePost(authUser.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글 목록 조회", description = "카테고리, 검색어, 해결 여부 등으로 필터링하여 조회합니다.")
    @GetMapping("/posts")
    public ResponseEntity<Page<PostResponse>> getPosts(
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isSolved,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getPosts(category, keyword, isSolved, pageable));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 내용과 계층형 댓글 목록(작성자 정보 포함)을 반환합니다.")
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(@PathVariable Long postId,
                                                            @AuthenticationPrincipal AuthUser authUser) {
        // 로그인하지 않은 경우 authUser는 null
        Long currentUserId = (authUser != null) ? authUser.getUserId() : null;
        return ResponseEntity.ok(postService.getPostDetail(postId, currentUserId));
    }

    @Operation(summary = "북마크 토글", description = "게시글을 북마크하거나 취소합니다.")
    @PostMapping("/posts/{postId}/bookmark")
    public ResponseEntity<Void> toggleBookmark(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long postId) {
        postService.toggleBookmark(authUser.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[QnA] 답변 채택", description = "질문 작성자가 특정 답변을 채택합니다.")
    @PostMapping("/posts/{postId}/comments/{commentId}/adopt")
    public ResponseEntity<Void> adoptAnswer(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long postId,
                                            @PathVariable Long commentId) {
        postService.adoptAnswer(authUser.getUserId(), postId, commentId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다.")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Void> createComment(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long postId,
                                              @RequestBody @Valid CommentRequest request) {
        postService.createComment(authUser.getUserId(), postId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 수정", description = "댓글 내용을 수정합니다.")
    @PatchMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> updateComment(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long postId,
                                              @PathVariable Long commentId,
                                              @RequestBody @Valid CommentRequest request) {
        postService.updateComment(authUser.getUserId(), postId, commentId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다.")
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long postId,
                                              @PathVariable Long commentId) {
        postService.deleteComment(authUser.getUserId(), postId, commentId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Team] 지원하기", description = "팀원 모집 글 작성자에게 1:1 채팅으로 지원 메시지를 보냅니다.")
    @PostMapping("/posts/{postId}/recruit/apply")
    public ResponseEntity<Void> applyTeam(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable Long postId) {
        postService.applyTeam(authUser.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "모집 상태 변경", description = "팀원 모집 상태를 변경합니다 (OPEN/CLOSED).")
    @PatchMapping("/posts/{postId}/status")
    public ResponseEntity<Void> updateRecruitmentStatus(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long postId,
                                                        @RequestBody RecruitmentStatusRequest request) {
        postService.updateRecruitmentStatus(authUser.getUserId(), postId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내가 쓴 게시글 조회", description = "로그인한 사용자가 작성한 게시글 목록을 조회합니다.")
    @GetMapping("/posts/me")
    public ResponseEntity<Page<PostResponse>> getMyPosts(@AuthenticationPrincipal AuthUser authUser,
                                                         @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getMyPosts(authUser.getUserId(), pageable));
    }

    @Operation(summary = "내가 북마크한 글 조회", description = "로그인한 사용자가 북마크한 게시글 목록을 조회합니다.")
    @GetMapping("/posts/me/bookmarks")
    public ResponseEntity<Page<PostResponse>> getMyBookmarkedPosts(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getMyBookmarkedPosts(authUser.getUserId(), pageable));
    }

    @Operation(summary = "내가 댓글 단 글 조회", description = "로그인한 사용자가 댓글을 작성한 게시글 목록을 조회합니다.")
    @GetMapping("/posts/me/commented")
    public ResponseEntity<Page<PostResponse>> getMyCommentedPosts(@AuthenticationPrincipal AuthUser authUser,
                                                                  @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getMyCommentedPosts(authUser.getUserId(), pageable));
    }

}