package com.example.authservice.controller;

import com.example.authservice.dto.request.EmailRequest;
import com.example.authservice.dto.request.VerificationRequest;
import com.example.authservice.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/email-verification")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    // 회원가입 - 인증 코드 발송
    @PostMapping("/send")
    public ResponseEntity<Void> sendVerificationCode(@RequestBody EmailRequest request) {
        emailVerificationService.sendSignUpCode(request.getEmail());
        return ResponseEntity.ok().build();
    }

    // 회원가입 - 인증 코드 검증
    @PostMapping("/check")
    public ResponseEntity<Void> checkVerificationCode(@RequestBody VerificationRequest request) {
        emailVerificationService.verifySignUpCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok().build();
    }
}