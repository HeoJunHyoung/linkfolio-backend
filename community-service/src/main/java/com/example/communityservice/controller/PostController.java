package com.example.communityservice.controller;

import com.example.commonmodule.dto.security.AuthUser;
import com.example.commonmodule.exception.ErrorResponse;
import com.example.communityservice.dto.request.CommentRequest;
import com.example.communityservice.dto.request.PostCreateRequest;
import com.example.communityservice.dto.request.PostUpdateRequest;
import com.example.communityservice.dto.request.RecruitmentStatusRequest;
import com.example.communityservice.dto.response.*;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Tag(name = "Community API", description = "커뮤니티 게시글, 댓글, 북마크 및 모집 관리 API")
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 작성", description = "새로운 게시글(QnA, 정보공유, 팀원모집)을 작성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "작성 성공 (생성된 게시글 ID 반환)"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력값 [G002]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PostMapping("/posts")
    public ResponseEntity<Long> createPost(@AuthenticationPrincipal AuthUser authUser,
                                           @RequestBody @Valid PostCreateRequest request) {
        return ResponseEntity.ok(postService.createPost(authUser.getUserId(), request));
    }

    @Operation(summary = "게시글 수정", description = "본인이 작성한 게시글의 제목과 내용을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "작성자만 수정 가능 [C002]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시글 찾을 수 없음 [C001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PutMapping("/posts/{postId}")
    public ResponseEntity<Void> updatePost(@AuthenticationPrincipal AuthUser authUser,
                                           @Parameter(description = "수정할 게시글 ID") @PathVariable Long postId,
                                           @RequestBody @Valid PostUpdateRequest request) {
        postService.updatePost(authUser.getUserId(), postId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글 삭제", description = "본인이 작성한 게시글을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "작성자만 삭제 가능 [C002]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "게시글 찾을 수 없음 [C001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthUser authUser,
                                           @Parameter(description = "삭제할 게시글 ID") @PathVariable Long postId) {
        postService.deletePost(authUser.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "게시글 목록 조회 (검색/필터)", description = "카테고리, 키워드, 해결 여부 등을 조건으로 게시글 목록을 조회합니다. (인증 불필요)")
    @GetMapping("/posts")
    public ResponseEntity<CustomPageResponse<PostResponse>> getPosts(
            @Parameter(description = "카테고리 (QNA, INFO, RECRUIT)") @RequestParam(required = false) PostCategory category,
            @Parameter(description = "해결 여부 (QnA 전용)") @RequestParam(required = false) Boolean isSolved,
            @Parameter(description = "페이징 설정 (기본: 최신순 10개)") @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<PostResponse> page = postService.getPosts(category, isSolved, pageable);
        return ResponseEntity.ok(new CustomPageResponse<>(page));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글의 상세 내용과 계층형 댓글 목록을 조회합니다. 로그인 시 북마크 여부가 포함됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "게시글 찾을 수 없음 [C001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(@Parameter(description = "조회할 게시글 ID") @PathVariable Long postId,
                                                            @AuthenticationPrincipal AuthUser authUser) {
        Long currentUserId = (authUser != null) ? authUser.getUserId() : null;
        return ResponseEntity.ok(postService.getPostDetail(postId, currentUserId));
    }

    @Operation(summary = "북마크 토글", description = "게시글을 북마크에 추가하거나 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토글 성공"),
            @ApiResponse(responseCode = "404", description = "게시글 찾을 수 없음 [C001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PostMapping("/posts/{postId}/bookmark")
    public ResponseEntity<Void> toggleBookmark(@AuthenticationPrincipal AuthUser authUser,
                                               @PathVariable Long postId) {
        postService.toggleBookmark(authUser.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[QnA] 답변 채택", description = "QnA 게시글의 작성자가 특정 댓글(답변)을 채택합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "채택 성공"),
            @ApiResponse(responseCode = "400", description = "QnA 게시글이 아님 [C004]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "게시글 작성자만 채택 가능 [C002]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "댓글 찾을 수 없음 [C003]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PostMapping("/posts/{postId}/comments/{commentId}/adopt")
    public ResponseEntity<Void> adoptAnswer(@AuthenticationPrincipal AuthUser authUser,
                                            @PathVariable Long postId,
                                            @PathVariable Long commentId) {
        postService.adoptAnswer(authUser.getUserId(), postId, commentId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 작성", description = "게시글에 댓글(또는 대댓글)을 작성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "작성 성공"),
            @ApiResponse(responseCode = "404", description = "게시글 또는 부모 댓글 없음 [C001, C003]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Void> createComment(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long postId,
                                              @RequestBody @Valid CommentRequest request) {
        postService.createComment(authUser.getUserId(), postId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 수정", description = "본인이 작성한 댓글을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "댓글 작성자만 수정 가능 [C008]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "댓글 찾을 수 없음 [C003]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PatchMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> updateComment(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long postId,
                                              @PathVariable Long commentId,
                                              @RequestBody @Valid CommentRequest request) {
        postService.updateComment(authUser.getUserId(), postId, commentId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "댓글 삭제", description = "본인이 작성한 댓글을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "댓글 작성자만 삭제 가능 [C008]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "댓글 찾을 수 없음 [C003]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable Long postId,
                                              @PathVariable Long commentId) {
        postService.deleteComment(authUser.getUserId(), postId, commentId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Team] 지원하기", description = "모집 중인 팀원 모집 게시글 작성자에게 지원 메시지(1:1 채팅)를 보냅니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "지원 메시지 전송 성공"),
            @ApiResponse(responseCode = "400", description = "모집글이 아니거나 마감됨 [C005, C006]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "채팅 서버 오류 [C007]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PostMapping("/posts/{postId}/recruit/apply")
    public ResponseEntity<Void> applyTeam(@AuthenticationPrincipal AuthUser authUser,
                                          @PathVariable Long postId) {
        postService.applyTeam(authUser.getUserId(), postId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Team] 모집 상태 변경", description = "팀원 모집 상태를 변경합니다 (OPEN <-> CLOSED).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @ApiResponse(responseCode = "400", description = "모집글이 아님 [C005]", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성자만 변경 가능 [C002]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PatchMapping("/posts/{postId}/status")
    public ResponseEntity<Void> updateRecruitmentStatus(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long postId,
                                                        @RequestBody RecruitmentStatusRequest request) {
        postService.updateRecruitmentStatus(authUser.getUserId(), postId, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "내가 쓴 게시글 조회", description = "로그인한 사용자가 작성한 게시글 목록을 조회합니다. (카테고리 필터링 가능)")
    @SecurityRequirement(name = "BearerAuthentication")
    @GetMapping("/posts/me")
    public ResponseEntity<CustomPageResponse<MyPostResponse>> getMyPosts(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "카테고리 (QNA, INFO, RECRUIT), 미입력 시 전체") @RequestParam(required = false) PostCategory category,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MyPostResponse> page = postService.getMyPosts(authUser.getUserId(), category, pageable);
        return ResponseEntity.ok(new CustomPageResponse<>(page));
    }

    @Operation(summary = "내가 북마크한 글 조회", description = "로그인한 사용자가 북마크한 게시글 목록을 조회합니다. (카테고리 필터링 가능)")
    @SecurityRequirement(name = "BearerAuthentication")
    @GetMapping("/posts/me/bookmarks")
    public ResponseEntity<CustomPageResponse<MyBookmarkPostResponse>> getMyBookmarkedPosts(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "카테고리 (QNA, INFO, RECRUIT), 미입력 시 전체") @RequestParam(required = false) PostCategory category,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MyBookmarkPostResponse> page = postService.getMyBookmarkedPosts(authUser.getUserId(), category, pageable);
        return ResponseEntity.ok(new CustomPageResponse<>(page));
    }

}