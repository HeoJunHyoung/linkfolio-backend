package com.example.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    // (Internal) user-service에 프로필 생성을 요청
    @PostMapping("/user-service/internal/users/create")
    ResponseEntity<UserResponseDto> createUserProfile(@RequestBody UserSignUpRequest profileRequest);

    // (Internal) user-service에 이름+이메일로 유저(ID) 조회를 요청
    @PostMapping("/user-service/internal/users/find-by-profile")
    ResponseEntity<UserResponseDto> findUserByProfile(@RequestBody FindUsernameRequest profileRequest);

    // (Internal) user-service에 이메일 중복 검사를 요청
    @PostMapping("/user-service/internal/users/validate-email")
    ResponseEntity<Void> validateEmail(@RequestBody EmailRequest emailRequest);

    // (Internal) user-service에 이메일 인증 완료 여부 확인
    @PostMapping("/user-service/internal/users/is-email-verified")
    ResponseEntity<Boolean> isEmailVerified(@RequestBody EmailRequest emailRequest);

    // (Internal) OAuth 유저 생성/조회
    @PostMapping("/user-service/internal/users/social")
    ResponseEntity<UserResponseDto> saveOrUpdateSocialUser(@RequestBody OAuthAttributes attributes);

}
