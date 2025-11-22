package com.example.supportservice.controller;

import com.example.supportservice.dto.request.NoticeRequest;
import com.example.supportservice.dto.response.NoticeResponse;
import com.example.supportservice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notice API", description = "공지사항 관리 API")
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // ==========================
    // Public API (인증 불필요)
    // ==========================

    @Operation(summary = "공지사항 목록 조회", description = "모든 사용자가 접근 가능합니다.")
    @GetMapping("/notices")
    public ResponseEntity<Page<NoticeResponse>> getNotices(
            @PageableDefault(size = 10, sort = {"isImportant", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noticeService.getNotices(pageable));
    }

    @Operation(summary = "공지사항 상세 조회", description = "모든 사용자가 접근 가능합니다.")
    @GetMapping("/notices/{id}")
    public ResponseEntity<NoticeResponse> getNotice(@PathVariable Long id) {
        return ResponseEntity.ok(noticeService.getNotice(id));
    }

    // ==========================
    // Admin API (ADMIN 권한 필요)
    // ==========================

    @Operation(summary = "[Admin] 공지사항 등록")
    @PostMapping("/admin/notices")
    public ResponseEntity<Void> createNotice(@RequestBody @Valid NoticeRequest request) {
        noticeService.createNotice(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "[Admin] 공지사항 수정")
    @PutMapping("/admin/notices/{id}")
    public ResponseEntity<Void> updateNotice(@PathVariable Long id, @RequestBody @Valid NoticeRequest request) {
        noticeService.updateNotice(id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Admin] 공지사항 삭제")
    @DeleteMapping("/admin/notices/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.ok().build();
    }
}