package com.example.userservice.service;

import com.example.userservice.entity.UserProvider;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;
    private final UserRepository userRepository;

    // --- 공통 상수 ---
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    // --- 회원가입 인증 코드용 ---
    private static final String VERIFICATION_CODE_PREFIX = "VC:"; // 인증 코드
    private static final String VERIFIED_EMAIL_PREFIX = "VE:";    // 인증 완료된 이메일
    private static final long CODE_EXPIRATION_MINUTES = 3;       // 코드 만료 시간 (3분)
    private static final long VERIFIED_EXPIRATION_MINUTES = 5;  // 인증 완료 상태 만료 시간 (5분)

    // --- 비밀번호 재설정 인증 코드용 ---
    private static final String PW_RESET_CODE_PREFIX = "PW_RESET:";
    private static final long PW_RESET_CODE_EXPIRATION_MINUTES = 5; // 5분


    // ========================//
    // == 회원가입 인증 플로우 ==//
    // ========================//

    /**
     * 1. 회원가입 인증 코드 발송
     */
    public void sendCode(String email) {
        validateEmailDuplicate(email);
        String code = generateRandomCode();
        String key = VERIFICATION_CODE_PREFIX + email;

        storeCode(key, code, CODE_EXPIRATION_MINUTES);
        emailService.sendVerificationCode(email, code);
    }

    /**
     * 2. 회원가입 인증 코드 검증
     */
    public void verifyCode(String email, String code) {
        String key = VERIFICATION_CODE_PREFIX + email;

        // 2-1. 코드 검증
        validateCode(key, code, ErrorCode.VERIFICATION_CODE_EXPIRED, ErrorCode.INVALID_VERIFICATION_CODE);

        // 2-2. 검증 성공 시, 인증 코드 삭제
        deleteKey(key);

        // 2-3. "인증 완료" 상태를 Redis에 저장
        String verifiedKey = VERIFIED_EMAIL_PREFIX + email;
        storeCode(verifiedKey, "true", VERIFIED_EXPIRATION_MINUTES);
    }

    /**
     * 3. (회원가입 시) 이메일이 인증되었는지 확인
     */
    public boolean isEmailVerified(String email) {
        String verifiedKey = VERIFIED_EMAIL_PREFIX + email;
        String status = (String) redisTemplate.opsForValue().get(verifiedKey);
        return "true".equals(status);
    }

    /**
     * 4. (회원가입 완료 시) 인증 완료 상태 삭제
     */
    public void deleteVerifiedEmailStatus(String email) {
        deleteKey(VERIFIED_EMAIL_PREFIX + email);
    }


    // ============================//
    // == 비밀번호 재설정 플로우 ==//
    // ============================//

    /**
     * 5. 비밀번호 재설정 코드 발송
     */
    public void sendPasswordResetCode(String username, String email) {
        // [리팩터링] generateRandomCode() 재사용
        String code = generateRandomCode();
        String key = PW_RESET_CODE_PREFIX + username; // 이메일 대신 username(ID) 기준

        storeCode(key, code, PW_RESET_CODE_EXPIRATION_MINUTES);
        emailService.sendPasswordResetCode(email, code);
    }

    /**
     * 6. 비밀번호 재설정 코드 검증
     */
    public void verifyPasswordResetCode(String username, String code) {
        String key = PW_RESET_CODE_PREFIX + username;
        validateCode(key, code, ErrorCode.PASSWORD_RESET_CODE_EXPIRED, ErrorCode.INVALID_PASSWORD_RESET_CODE);
    }

    /**
     * 7. 비밀번호 재설정 코드 삭제
     */
    public void deletePasswordResetCode(String username) {
        deleteKey(PW_RESET_CODE_PREFIX + username);
    }


    //********************//
    //== 내부 헬퍼 메서드 ==//
    //********************//

    /**
     * Redis에 코드 저장
     */
    private void storeCode(String key, String code, long expirationMinutes) {
        redisTemplate.opsForValue().set(key, code, expirationMinutes, TimeUnit.MINUTES);
    }

    /**
     * Redis의 코드 검증
     */
    private void validateCode(String key, String providedCode, ErrorCode expiredErrorCode, ErrorCode invalidErrorCode) {
        String storedCode = (String) redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            throw new BusinessException(expiredErrorCode);
        }
        if (!storedCode.equals(providedCode)) {
            throw new BusinessException(invalidErrorCode);
        }
    }

    /**
     * Redis 키 삭제
     */
    private void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 6자리 랜덤 인증 코드 생성 (알파벳 대문자 + 숫자)
     */
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    /**
     * 회원가입 시 이메일 중복 검증
     */
    private void validateEmailDuplicate(String email) {
        userRepository.findUserDetailsByEmail(email).ifPresent(user -> {
            if (user.getProvider() == UserProvider.LOCAL) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
            } else {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED_AS_SOCIAL);
            }
        });
    }
}