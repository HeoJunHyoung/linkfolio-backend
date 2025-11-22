package com.example.supportservice.controller;

import com.example.commonmodule.exception.ErrorResponse;
import com.example.supportservice.dto.request.FaqRequest;
import com.example.supportservice.dto.response.FaqResponse;
import com.example.supportservice.entity.enumerate.FaqCategory;
import com.example.supportservice.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "FAQ API", description = "자주 묻는 질문(FAQ) 관리 API")
@RestController
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    // ==========================
    // Public API (인증 불필요)
    // ==========================

    @Operation(summary = "FAQ 목록 조회", description = "카테고리별 혹은 전체 FAQ 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/faqs")
    public ResponseEntity<List<FaqResponse>> getFaqs(
            @Parameter(description = "조회할 카테고리 (ACCOUNT, PORTFOLIO, PAYMENT, ETC). 생략 시 전체 조회")
            @RequestParam(required = false) FaqCategory category) {

        if (category != null) {
            return ResponseEntity.ok(faqService.getFaqsByCategory(category));
        }
        return ResponseEntity.ok(faqService.getAllFaqs());
    }

    // ==========================
    // Admin API (ADMIN 권한 필요)
    // ==========================

    @Operation(summary = "[Admin] FAQ 등록", description = "새로운 FAQ를 등록합니다. (ADMIN 권한 필수)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 (질문/답변/카테고리 누락)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/admin/faqs")
    public ResponseEntity<Void> createFaq(@RequestBody @Valid FaqRequest request) {
        faqService.createFaq(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "[Admin] FAQ 수정", description = "기존 FAQ를 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "FAQ를 찾을 수 없음 [F001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/admin/faqs/{id}")
    public ResponseEntity<Void> updateFaq(@PathVariable Long id, @RequestBody @Valid FaqRequest request) {
        faqService.updateFaq(id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Admin] FAQ 삭제", description = "FAQ를 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "FAQ를 찾을 수 없음 [F001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/admin/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        faqService.deleteFaq(id);
        return ResponseEntity.ok().build();
    }
}