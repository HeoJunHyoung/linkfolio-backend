package com.example.authservice.controller;

import com.example.authservice.dto.request.EmailRequest;
import com.example.authservice.dto.request.VerificationRequest;
import com.example.authservice.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Email Verification API", description = "회원가입 시 이메일 인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/email-verification")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "회원가입 인증 코드 발송", description = "회원가입을 위한 6자리 인증 코드를 이메일로 발송합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "코드 발송 성공"),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일 (로컬/소셜) [U003, U004]", content = @Content)
    })
    @PostMapping("/send")
    public ResponseEntity<Void> sendVerificationCode(@RequestBody EmailRequest request) {
        emailVerificationService.sendSignUpCode(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "회원가입 인증 코드 검증", description = "발송된 인증 코드를 검증합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "코드 검증 성공"),
            @ApiResponse(responseCode = "400", description = "코드 만료 [U007] 또는 불일치 [U008]", content = @Content)
    })
    @PostMapping("/check")
    public ResponseEntity<Void> checkVerificationCode(@RequestBody VerificationRequest request) {
        emailVerificationService.verifySignUpCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok().build();
    }
}