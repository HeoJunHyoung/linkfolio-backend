package com.example.supportservice.controller;

import com.example.commonmodule.exception.ErrorResponse;
import com.example.supportservice.dto.request.NoticeRequest;
import com.example.supportservice.dto.response.NoticeResponse;
import com.example.supportservice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "Notice API", description = "공지사항 관리 API (Public: 조회 / Admin: 관리)")
@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    // ==========================
    // Public API (인증 불필요)
    // ==========================

    @Operation(summary = "공지사항 목록 조회", description = "모든 사용자가 접근 가능합니다. 중요 공지가 상단에 고정되며, 그 안에서는 최신순으로 정렬됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/notices")
    public ResponseEntity<Page<NoticeResponse>> getNotices(
            @PageableDefault(size = 10, sort = {"isImportant", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(noticeService.getNotices(pageable));
    }

    @Operation(summary = "공지사항 상세 조회", description = "공지사항 ID로 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "공지사항을 찾을 수 없음 [N001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/notices/{id}")
    public ResponseEntity<NoticeResponse> getNotice(@PathVariable Long id) {
        return ResponseEntity.ok(noticeService.getNotice(id));
    }

    // ==========================
    // Admin API (ADMIN 권한 필요)
    // ==========================

    @Operation(summary = "[Admin] 공지사항 등록", description = "새로운 공지사항을 등록합니다. (ADMIN 권한 필수)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패 (제목/내용 누락 등)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "권한 없음 (일반 사용자 접근 시)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/admin/notices")
    public ResponseEntity<Void> createNotice(@RequestBody @Valid NoticeRequest request) {
        noticeService.createNotice(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "[Admin] 공지사항 수정", description = "기존 공지사항을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "공지사항을 찾을 수 없음 [N001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/admin/notices/{id}")
    public ResponseEntity<Void> updateNotice(@PathVariable Long id, @RequestBody @Valid NoticeRequest request) {
        noticeService.updateNotice(id, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "[Admin] 공지사항 삭제", description = "공지사항을 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "공지사항을 찾을 수 없음 [N001]", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/admin/notices/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable Long id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.ok().build();
    }
}