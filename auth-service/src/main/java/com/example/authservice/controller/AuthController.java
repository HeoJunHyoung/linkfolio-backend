package com.example.authservice.controller;

import com.example.authservice.dto.request.*;
import com.example.authservice.dto.response.TokenResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.RefreshTokenService;
import com.example.authservice.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<Void> signUpApi(@RequestBody UserSignUpRequest request) {
        authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 회원가입 - ID(username) 중복 검사
    @PostMapping("/check-username")
    public ResponseEntity<Void> checkUsernameApi(@RequestBody CheckUsernameRequest request) {
        authService.validateUsernameDuplicate(request.getUsername());
        return ResponseEntity.ok().build();
    }

    // 회원가입 - 비밀번호 일치 확인
    @PostMapping("/check-password")
    public ResponseEntity<Void> checkPasswordApi(@RequestBody CheckPasswordRequest request) {
        authService.validatePasswordMatch(request.getPassword(), request.getPasswordConfirm());
        return ResponseEntity.ok().build();
    }

    // 로그인
    @PostMapping("/login")
    public void login(@RequestBody UserLoginRequest request) {
        // CustomAuthenticationFilter가 처리
    }

    // token 재발급
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


    // ID(username) 찾기
    @PostMapping("/find-username")
    public ResponseEntity<String> findUsernameApi(@RequestBody FindUsernameRequest request) {
        String username = authService.findUsername(request);
        return ResponseEntity.ok(username);
    }

    // 비밀번호 재설정 [1]: 인증 코드 발송
    @PostMapping("/password-reset/send-code")
    public ResponseEntity<Void> sendPasswordResetCodeApi(@RequestBody PasswordResetSendCodeRequest request) {
        authService.sendPasswordResetCode(request);
        return ResponseEntity.ok().build();
    }

    // 비밀번호 재설정 [2]: 인증 코드 검증
    @PostMapping("/password-reset/verify-code")
    public ResponseEntity<Void> verifyPasswordResetCodeApi(@RequestBody PasswordResetVerifyCodeRequest request) {
        authService.verifyPasswordResetCode(request);
        return ResponseEntity.ok().build();
    }

    // 비밀번호 재설정 [3]: 인증 코드 확인 및 비밀번호 변경
    @PostMapping("/password-reset/change")
    public ResponseEntity<Void> resetPasswordApi(@RequestBody PasswordResetChangeRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-User-Id", required = false) Long gatewayUserId,
                                       HttpServletResponse response) {
        // 참고: 로그아웃은 게이트웨이를 통과하지 않고 바로 호출될 수도 (토큰 만료 등)
        // 게이트웨이가 준 ID가 있으면 그걸 쓰고, 없으면 쿠키의 RT로 파싱 (RefreshTokenService가 담당)
        // 여기서는 단순화하여 RT 쿠키만 만료시킴

        if (gatewayUserId != null) {
            refreshTokenService.deleteRefreshToken(gatewayUserId);
        }

        cookieUtil.expireRefreshTokenCookie(response);
        log.info("로그아웃 처리 완료. UserId: {}", gatewayUserId != null ? gatewayUserId : "Unknown (No Header)");
        return ResponseEntity.ok().build();
    }
}