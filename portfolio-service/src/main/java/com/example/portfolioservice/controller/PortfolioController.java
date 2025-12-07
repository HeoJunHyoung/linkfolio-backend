package com.example.portfolioservice.controller;

import com.example.commonmodule.dto.security.AuthUser;
import com.example.commonmodule.exception.ErrorResponse;
import com.example.portfolioservice.dto.request.PortfolioRequest;
import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioDetailsResponse;
import com.example.portfolioservice.service.PortfolioLikeService;
import com.example.portfolioservice.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Portfolio API", description = "포트폴리오 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping
@Slf4j
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioLikeService portfolioLikeService;

    @Operation(summary = "내 포트폴리오 조회 (마이페이지)", description = "자동 고정되는 프로필 정보 포함. (자가 치유)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = PortfolioDetailsResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @GetMapping("/me")
    public ResponseEntity<PortfolioDetailsResponse> getMyPortfolioApi(@AuthenticationPrincipal AuthUser authUser) {
        PortfolioDetailsResponse response = portfolioService.getMyPortfolio(authUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 포트폴리오 생성/수정 (마이페이지)", description = "사용자 입력 정보(사진, 자기소개, PR)를 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 성공", content = @Content(schema = @Schema(implementation = PortfolioDetailsResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PutMapping("/me")
    public ResponseEntity<PortfolioDetailsResponse> createOrUpdateMyPortfolioApi(@AuthenticationPrincipal AuthUser authUser,
                                                                                 @RequestBody PortfolioRequest request) {
        PortfolioDetailsResponse response = portfolioService.createOrUpdateMyPortfolio(authUser.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 카드 목록 조회 (메인 페이지)", description = "인증 불필요. 필터링 및 정렬(sort) 지원.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = Slice.class)))
    })
    @GetMapping("/portfolios")
    public ResponseEntity<Slice<PortfolioCardResponse>> getPortfolioListApi(@AuthenticationPrincipal AuthUser authUser,
                                                                            @PageableDefault(size = 8) Pageable pageable,
                                                                            @RequestParam(required = false) String position) {
        // 비로그인 사용자(null) 처리
        Long userId = (authUser != null) ? authUser.getUserId() : null;

        // Service 호출 시 userId 전달
        Slice<PortfolioCardResponse> response = portfolioService.getPortfolioList(userId, pageable, position);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "특정 포트폴리오 상세 조회 (상세보기)", description = "인증 불필요. 캐시된 정보로 응답합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = PortfolioDetailsResponse.class))),
            @ApiResponse(responseCode = "404", description = "포트폴리오 없음 [P001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/portfolios/{portfolioId}")
    public ResponseEntity<PortfolioDetailsResponse> getPortfolioDetailsApi(@PathVariable("portfolioId") Long portfolioId,
                                                                           @AuthenticationPrincipal AuthUser authUser) {
        PortfolioDetailsResponse response = portfolioService.getPortfolioDetails(portfolioId, authUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 북마크 추가", description = "특정 포트폴리오를 북마크로 추가합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "북마크 추가 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "포트폴리오 없음 [P001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PostMapping("/portfolios/{portfolioId}/like")
    public ResponseEntity<Void> addLikeApi(@AuthenticationPrincipal AuthUser authUser,
                                           @PathVariable("portfolioId") Long portfolioId) {
        portfolioLikeService.addLike(authUser.getUserId(), portfolioId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "포트폴리오 북마크 취소", description = "특정 포트폴리오에 대한 북마크를 취소합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "북마크 취소 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "포트폴리오 없음 [P001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @DeleteMapping("/portfolios/{portfolioId}/like")
    public ResponseEntity<Void> removeLikeApi(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable("portfolioId") Long portfolioId) {
        portfolioLikeService.removeLike(authUser.getUserId(), portfolioId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "내 관심 포트폴리오 목록 조회", description = "현재 로그인한 사용자가 관심을 누른 포트폴리오 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = Slice.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @GetMapping("/me/likes")
    public ResponseEntity<Slice<PortfolioCardResponse>> getMyLikedPortfoliosApi(@AuthenticationPrincipal AuthUser authUser,
                                                                                @RequestParam(required = false) String position,
                                                                                @PageableDefault(size = 8) Pageable pageable) {
        Slice<PortfolioCardResponse> response = portfolioLikeService.getMyLikedPortfolios(
                authUser.getUserId(),
                position,
                pageable
        );
        return ResponseEntity.ok(response);
    }
}