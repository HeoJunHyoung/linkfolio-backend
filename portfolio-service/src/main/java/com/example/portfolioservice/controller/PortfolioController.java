package com.example.portfolioservice.controller;

import com.example.portfolioservice.dto.AuthUser;
import com.example.portfolioservice.dto.request.PortfolioRequest;
import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioResponse;
import com.example.portfolioservice.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    @Operation(summary = "내 포트폴리오 조회 (마이페이지)", description = "자동 고정되는 프로필 정보 포함. (자가 치유)")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping("/me")
    public ResponseEntity<PortfolioResponse> getMyPortfolioApi(@AuthenticationPrincipal AuthUser authUser) {
        PortfolioResponse response = portfolioService.getMyPortfolio(authUser.getUserId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 포트폴리오 생성/수정 (마이페이지)", description = "사용자 입력 정보(사진, 자기소개, PR)를 저장합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    @PutMapping("/me")
    public ResponseEntity<PortfolioResponse> createOrUpdateMyPortfolioApi(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody PortfolioRequest request) {
        PortfolioResponse response = portfolioService.createOrUpdateMyPortfolio(authUser.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 카드 목록 조회 (메인 페이지)", description = "인증 불필요. 캐시된 정보로 응답합니다.")
    @GetMapping("/portfolios")
    public ResponseEntity<Page<PortfolioCardResponse>> getPortfolioListApi(@PageableDefault(size = 20) Pageable pageable) {
        Page<PortfolioCardResponse> response = portfolioService.getPortfolioList(pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "특정 포트폴리오 상세 조회 (상세보기)", description = "인증 불필요. 캐시된 정보로 응답합니다.")
    @GetMapping("/portfolios/{userId}")
    public ResponseEntity<PortfolioResponse> getPortfolioDetailsApi(@PathVariable("userId") Long userId) {
        PortfolioResponse response = portfolioService.getPortfolioDetails(userId);
        return ResponseEntity.ok(response);
    }
}