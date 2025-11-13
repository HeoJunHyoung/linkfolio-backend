package com.example.portfolioservice.controller;

import com.example.commonmodule.dto.security.AuthUser;
import com.example.portfolioservice.dto.request.PortfolioRequest;
import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioDetailsResponse;
import com.example.portfolioservice.service.PortfolioLikeService;
import com.example.portfolioservice.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
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
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/me")
    public ResponseEntity<PortfolioDetailsResponse> getMyPortfolioApi(@AuthenticationPrincipal AuthUser authUser) {
        PortfolioDetailsResponse response = portfolioService.getMyPortfolio(authUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 포트폴리오 생성/수정 (마이페이지)", description = "사용자 입력 정보(사진, 자기소개, PR)를 저장합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/me")
    public ResponseEntity<PortfolioDetailsResponse> createOrUpdateMyPortfolioApi(@AuthenticationPrincipal AuthUser authUser,
                                                                                 @RequestBody PortfolioRequest request) {
        PortfolioDetailsResponse response = portfolioService.createOrUpdateMyPortfolio(authUser.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 카드 목록 조회 (메인 페이지)", description = "인증 불필요. 필터링 및 정렬(sort) 지원. (예: ?position=백엔드&sort=likeCount,desc)")
    @GetMapping("/portfolios")
    public ResponseEntity<Slice<PortfolioCardResponse>> getPortfolioListApi(@PageableDefault(size = 8, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                                                            @RequestParam(required = false) String position) {
        Slice<PortfolioCardResponse> response = portfolioService.getPortfolioList(pageable, position);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "특정 포트폴리오 상세 조회 (상세보기)", description = "인증 불필요. 캐시된 정보로 응답합니다.")
    @GetMapping("/portfolios/{portfolioId}")
    public ResponseEntity<PortfolioDetailsResponse> getPortfolioDetailsApi(@PathVariable("portfolioId") Long portfolioId,
                                                                           @AuthenticationPrincipal AuthUser authUser) { // 만약 로그인 안됐으면 알아서 null 값 처리 해줌 (SecurityConfig에 permitAll() 설정 해줬음)
        PortfolioDetailsResponse response = portfolioService.getPortfolioDetails(portfolioId, authUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 관심 추가", description = "특정 포트폴리오에 관심을 추가합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping("/portfolios/{portfolioId}/like")
    public ResponseEntity<Void> addLikeApi(@AuthenticationPrincipal AuthUser authUser,
                                           @PathVariable("portfolioId") Long portfolioId) {
        portfolioLikeService.addLike(authUser.getUserId(), portfolioId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "포트폴리오 관심 취소", description = "특정 포트폴리오에 대한 관심을 취소합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping("/portfolios/{portfolioId}/like")
    public ResponseEntity<Void> removeLikeApi(@AuthenticationPrincipal AuthUser authUser,
                                              @PathVariable("portfolioId") Long portfolioId) {
        portfolioLikeService.removeLike(authUser.getUserId(), portfolioId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "내 관심 포트폴리오 목록 조회", description = "현재 로그인한 사용자가 관심을 누른 포트폴리오 목록을 조회합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/me/likes")
    public ResponseEntity<Slice<PortfolioCardResponse>> getMyLikedPortfoliosApi(
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 8) Pageable pageable) {
        Slice<PortfolioCardResponse> response = portfolioLikeService.getMyLikedPortfolios(authUser.getUserId(), pageable);
        return ResponseEntity.ok(response);
    }

}