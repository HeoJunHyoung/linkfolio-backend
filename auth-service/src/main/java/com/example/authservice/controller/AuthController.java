package com.example.authservice.controller;

import com.example.authservice.dto.request.*;
import com.example.authservice.dto.response.FindUsernameResponse;
import com.example.authservice.dto.response.TokenResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.RefreshTokenService;
import com.example.authservice.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Tag(name = "Auth API", description = "회원가입, 로그인, 토큰 관리, 계정 찾기 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @Operation(summary = "로컬(자체) 회원가입", description = "신규 회원을 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "403", description = "이메일 미인증 [U009]", content = @Content),
            @ApiResponse(responseCode = "409", description = "ID 중복 [U006] 또는 이메일 중복 [U003, U004]", content = @Content)
    })
    @PostMapping("/signup")
    public ResponseEntity<Void> signUpApi(@RequestBody UserSignUpRequest request) {
        authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


    @Operation(summary = "ID(username) 중복 검사", description = "회원가입 시 사용할 ID의 중복 여부를 확인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "사용 가능한 ID"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 ID [U006]", content = @Content)
    })
    @PostMapping("/check-username")
    public ResponseEntity<Void> checkUsernameApi(@RequestBody CheckUsernameRequest request) {
        authService.validateUsernameDuplicate(request.getUsername());
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "비밀번호 일치 확인", description = "회원가입 시 비밀번호와 비밀번호 확인이 일치하는지 검사합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비밀번호 일치"),
            @ApiResponse(responseCode = "400", description = "비밀번호 불일치 [U002]", content = @Content)
    })
    @PostMapping("/check-password")
    public ResponseEntity<Void> checkPasswordApi(@RequestBody CheckPasswordRequest request) {
        authService.validatePasswordMatch(request.getPassword(), request.getPasswordConfirm());
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "로컬(자체) 로그인 (ID/PW)", description = "ID와 비밀번호로 로그인하고 토큰을 발급받습니다. (실제 처리는 Filter)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인 실패 (비밀번호 불일치 등)", content = @Content)
    })
    @PostMapping("/login")
    public void login(@RequestBody UserLoginRequest request) {
        // CustomAuthenticationFilter가 처리
    }


    @Operation(summary = "액세스 토큰 재발급", description = "HttpOnly 쿠키의 Refresh Token을 사용하여 새 Access Token을 발급받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토큰 재발급 성공", content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token 없음, 만료, 또는 탈취 의심 [T001, T002, T003]", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshAccessToken(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                                            HttpServletResponse response) {
        if (refreshToken == null) {
            log.warn("Refresh Token 쿠키가 존재하지 않습니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, String> newTokens = refreshTokenService.reissueTokens(refreshToken);

        String newAccessToken = newTokens.get("accessToken");
        String newRefreshToken = newTokens.get("refreshToken");

        cookieUtil.addRefreshTokenCookie(response, newRefreshToken); // refresh 토큰 전송
        return ResponseEntity.ok(new TokenResponse(newAccessToken)); // access 토큰 전송
    }


    @Operation(summary = "아이디(username) 찾기", description = "실명과 이메일로 가입된 ID(username)를 찾습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "ID 찾기 성공",
                    content = @Content(schema = @Schema(implementation = FindUsernameResponse.class))), //
            @ApiResponse(responseCode = "404", description = "정보 불일치 (로컬 계정 없음) [U001_1]", content = @Content)
    })
    @PostMapping("/find-username")
    public ResponseEntity<FindUsernameResponse> findUsernameApi(@RequestBody FindUsernameRequest request) {
        FindUsernameResponse response = authService.findUsername(request);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "비밀번호 재설정 [1]: 인증 코드 발송", description = "비밀번호 재설정을 위한 인증 코드를 이메일로 발송합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "코드 발송 성공"),
            @ApiResponse(responseCode = "404", description = "가입된 이메일 없음 [U001]", content = @Content)
    })
    @PostMapping("/password-reset/send-code")
    public ResponseEntity<Void> sendPasswordResetCodeApi(@RequestBody PasswordResetSendCodeRequest request) {
        authService.sendPasswordResetCode(request);
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "비밀번호 재설정 [2]: 인증 코드 검증", description = "발송된 인증 코드를 검증합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "코드 검증 성공"),
            @ApiResponse(responseCode = "400", description = "코드 만료 [U010] 또는 불일치 [U011]", content = @Content)
    })
    @PostMapping("/password-reset/verify-code")
    public ResponseEntity<Void> verifyPasswordResetCodeApi(@RequestBody PasswordResetVerifyCodeRequest request) {
        authService.verifyPasswordResetCode(request);
        return ResponseEntity.ok().build();
    }


    @Operation(summary = "비밀번호 재설정 [3]: 비밀번호 변경", description = "인증 완료 후, 새 비밀번호로 변경합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "코드 검증 미완료 [U010] 또는 비밀번호 불일치 [U002]", content = @Content)
    })
    @PostMapping("/password-reset/change")
    public ResponseEntity<Void> resetPasswordApi(@RequestBody PasswordResetChangeRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "마이페이지 비밀번호 변경", description = "현재 로그인된 사용자의 비밀번호를 변경합니다. (인증 필요)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "비밀번호 변경 성공"),
            @ApiResponse(responseCode = "400", description = "기존 비밀번호 불일치 또는 새 비밀번호 불일치 [U002]", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패 (게이트웨이)", content = @Content),
            @ApiResponse(responseCode = "404", description = "사용자 없음 [U001]", content = @Content)
    })
    @SecurityRequirement(name = "BearerAuthentication")
    @PatchMapping("/password")
    public ResponseEntity<Void> changePasswordApi(@RequestHeader(value = "X-User-Id") Long userId,
                                                  @RequestBody PasswordChangeRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "로그아웃", description = "서버의 Refresh Token을 만료시키고 쿠키를 삭제합니다.")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @SecurityRequirement(name = "BearerAuthentication") // 이 API는 인증이 필요함을 명시
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId,
                                       HttpServletResponse response) {

        if (gatewayUserId != null) {
            refreshTokenService.deleteRefreshToken(gatewayUserId);
        }

        cookieUtil.expireRefreshTokenCookie(response);
        log.info("로그아웃 처리 완료. UserId: {}", gatewayUserId != null ? gatewayUserId : "Unknown (No Header)");
        return ResponseEntity.ok().build();
    }
}