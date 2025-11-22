package com.example.supportservice.controller;

import com.example.supportservice.dto.request.FaqRequest;
import com.example.supportservice.dto.response.FaqResponse;
import com.example.supportservice.entity.enumerate.FaqCategory;
import com.example.supportservice.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "FAQ 목록 조회", description = "category 파라미터가 없으면 전체 조회")
    @GetMapping("/faqs")
    public ResponseEntity<List<FaqResponse>> getFaqs(@RequestParam(required = false) FaqCategory category) {
        if (category != null) {
            return ResponseEntity.ok(faqService.getFaqsByCategory(category));
        }
        return ResponseEntity.ok(faqService.getAllFaqs());
    }

    // ==========================
    // Admin API (ADMIN 권한 필요)
    // ==========================

    @Operation(summary = "[Admin] FAQ 등록")
    @PostMapping("/admin/faqs")
    public ResponseEntity<Void> createFaq(@RequestBody @Valid FaqRequest request) {
        faqService.createFaq(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "[Admin] FAQ 수정")
    @PutMapping("/admin/faqs/{id}")
    public ResponseEntity<Void> updateFaq(@PathVariable Long id, @RequestBody @Valid FaqRequest request) {
        faqService.updateFaq(id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Admin] FAQ 삭제")
    @DeleteMapping("/admin/faqs/{id}")
    public ResponseEntity<Void> deleteFaq(@PathVariable Long id) {
        faqService.deleteFaq(id);
        return ResponseEntity.ok().build();
    }
}