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
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;
    private final UserRepository userRepository;

    private static final String VERIFICATION_CODE_PREFIX = "VC:"; // 인증 코드
    private static final String VERIFIED_EMAIL_PREFIX = "VE:";    // 인증 완료된 이메일
    private static final long CODE_EXPIRATION_MINUTES = 3;       // 코드 만료 시간 (3분)
    private static final long VERIFIED_EXPIRATION_MINUTES = 5;  // 인증 완료 상태 만료 시간 (5분)

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    /**
     * 1. 인증 코드 발송
     */
    public void sendCode(String email) {
        // 1-1. 이메일 중복 검사 (UserService의 메서드 활용)
        validateEmailDuplicate(email);

        String code = generateRandomCode();
        String key = VERIFICATION_CODE_PREFIX + email;

        // 1-2. Redis에 인증 코드 저장 (5분 만료)
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);

        // 1-3. 이메일 발송
        emailService.sendVerificationCode(email, code);
    }

    /**
     * 2. 인증 코드 검증
     */
    public void verifyCode(String email, String code) {
        String key = VERIFICATION_CODE_PREFIX + email;
        String storedCode = (String) redisTemplate.opsForValue().get(key);

        // 2-1. 코드가 없거나 만료됨
        if (storedCode == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED); // (ErrorCode 추가 필요)
        }

        // 2-2. 코드가 일치하지 않음
        if (!storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE); // (ErrorCode 추가 필요)
        }

        // 2-3. 검증 성공 시, 인증 코드 삭제
        redisTemplate.delete(key);

        // 2-4. "인증 완료" 상태를 Redis에 저장 (5분 만료)
        String verifiedKey = VERIFIED_EMAIL_PREFIX + email;
        redisTemplate.opsForValue().set(verifiedKey, "true", VERIFIED_EXPIRATION_MINUTES, TimeUnit.MINUTES);
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
        String verifiedKey = VERIFIED_EMAIL_PREFIX + email;
        redisTemplate.delete(verifiedKey);
    }

    // 6자리 숫자 인증 코드 생성
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }

    private void validateEmailDuplicate(String email) {
        userRepository.findUserDetailsByEmail(email).ifPresent(user -> {
            if (user.getProvider() == UserProvider.LOCAL) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
            } else {
                // (ErrorCode에 EMAIL_ALREADY_REGISTERED_AS_SOCIAL이 정의되어 있어야 함)
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED_AS_SOCIAL);
            }
        });
    }

}