package com.example.userservice.controller;

import com.example.userservice.client.dto.InternalUserProfileResponse;
import com.example.userservice.dto.*;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(name = "User API", description = "사용자 정보 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
@Slf4j
public class UserController {

    private final UserService userService;

    @Operation(summary = "Welcome API (인증 불필요)", description = "서비스 동작 확인용 Welcome API")
    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to LinkFolio Project";
    }


    @Operation(summary = "내 정보 조회", description = "현재 로그인된 사용자의 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 (게이트웨이)", content = @Content),
            @ApiResponse(responseCode = "404", description = "사용자 없음 [U001]", content = @Content)
    })
    @SecurityRequirement(name = "Bearer Authentication") // 이 API는 인증이 필요함
    @GetMapping("/users/me")
    public ResponseEntity<UserResponse> getMyInfoApi(@AuthenticationPrincipal AuthUser authUser) {
        UserResponse userResponse = userService.getUser(authUser.getUserId());
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

    @Operation(summary = "특정 회원 정보 조회", description = "사용자 ID(PK)로 특정 회원의 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패 (게이트웨이)", content = @Content),
            @ApiResponse(responseCode = "404", description = "사용자 없음 [U001]", content = @Content)
    })
    @SecurityRequirement(name = "Bearer Authentication") // 이 API는 인증이 필요함
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserResponse> getUserApi(@PathVariable("userId") Long userId) {
        UserResponse userResponse = userService.getUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

    @Operation(summary = "내부용 프로필 조회 (Feign용)", hidden = true) // Swagger 숨김 처리
    @GetMapping("/internal/users/{userId}")
    public ResponseEntity<InternalUserProfileResponse> getInternalUserProfileApi(@PathVariable("userId") Long userId) {
        InternalUserProfileResponse response = userService.getInternalUserProfile(userId);
        return ResponseEntity.ok(response);
    }
}
