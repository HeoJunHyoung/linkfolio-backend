package com.example.authservice.service;

import com.example.authservice.dto.UserDto;
import com.example.authservice.dto.request.*;
import com.example.authservice.dto.response.FindUsernameResponse;
import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.exception.ErrorCode;
import com.example.authservice.repository.AuthUserRepository;
import com.example.authservice.service.kafka.UserEventProducer;
import com.example.commonmodule.dto.event.UserRegistrationRequestedEvent;
import com.example.commonmodule.entity.enumerate.UserProvider;
import com.example.commonmodule.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final UserEventProducer userEventProducer;

    /**
     * 회원가입 (Auth-Service가 주관)
     */
    @Transactional
    public void signUp(UserSignUpRequest request) {

        // 1. (Auth) 이메일 "인증 완료" 상태인지 확인 (Redis)
        if (!emailVerificationService.isEmailVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 2. (Auth) 비밀번호 확인
        validatePasswordMatch(request.getPassword(), request.getPasswordConfirm());

        // 3. (Auth) ID(username) 중복 검사 (Auth DB)
        validateUsernameDuplicate(request.getUsername());

        // 4. (Auth) 이메일 중복 검사 (Auth DB)
        validateEmailDuplicate(request.getEmail());

        // 5. (Auth) 인증 정보 생성 (Auth DB)
        // ㄴ ofLocal() 호출 시 PENDING 상태로 생성됨
        AuthUserEntity authUser = AuthUserEntity.ofLocal(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getUsername(),
                request.getName() // 이름도 Auth DB에 저장
        );
        AuthUserEntity savedAuthUser = authUserRepository.save(authUser);
        log.info("AuthUser 'PENDING' 상태로 저장됨. UserId: {}", savedAuthUser.getUserId());


        // 6. (Kafka) 프로필 생성을 위한 이벤트 발행
        try {
            UserRegistrationRequestedEvent event = new UserRegistrationRequestedEvent();
            event.setUserId(savedAuthUser.getUserId()); // [중요] Auth DB에서 생성된 ID
            event.setEmail(request.getEmail());
            event.setUsername(request.getUsername());
            event.setName(request.getName());
            event.setBirthdate(request.getBirthdate());
            event.setGender(request.getGender());
            event.setProvider(UserProvider.LOCAL.name());
            event.setRole(savedAuthUser.getRole());

            // UserEventProducer 사용 (Kafka 발행 실패 시 BusinessException이 throw되어 롤백됨)
            userEventProducer.sendUserRegistrationRequested(event);

        } catch (Exception e) {
            log.error("Kafka 이벤트 발행 실패 (Auth-Service 롤백 필요), UserId: {}", savedAuthUser.getUserId(), e);
            // AuthService의 @Transactional에 의해 이 RuntimeException이 롤백을 트리거
            // (UserEventProducer가 BusinessException을 던지도록 설정함)
            throw e;
        }

        // 7. (Auth) 회원가입 완료 후, Redis의 "인증 완료" 상태 삭제
        emailVerificationService.deleteVerifiedEmailStatus(request.getEmail());
    }

    private void validateEmailDuplicate(String email) {
        if (authUserRepository.findByEmailAndProvider(email, UserProvider.LOCAL).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
        }
    }

    // (Auth) ID 중복 검사
    public void validateUsernameDuplicate(String username) {
        if (authUserRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATION);
        }
    }

    /**
     * 아이디(username) 찾기
     */
    @Transactional(readOnly = true)
    public FindUsernameResponse findUsername(FindUsernameRequest request) {

        // 1. (Auth) Auth DB에서 이름+이메일+LOCAL provider로 직접 조회
        AuthUserEntity authUser = authUserRepository.findByNameAndEmailAndProvider(
                request.getName(),
                request.getEmail(),
                UserProvider.LOCAL
        ).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_NAME_AND_EMAIL));

        // 2. (Auth) DTO로 변환하여 반환
        return FindUsernameResponse.of(authUser.getUsername());
    }


    // (Auth) 비밀번호 일치 검사
    public void validatePasswordMatch(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    /**
     * (Auth) 비밀번호 재설정 [1]: 코드 발송
     */
    @Transactional(readOnly = true)
    public void sendPasswordResetCode(PasswordResetSendCodeRequest request) {
        // 1. (Auth) 이메일로 LOCAL 유저 조회
        AuthUserEntity userEntity = authUserRepository.findByEmailAndProvider(request.getEmail(), UserProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 인증 코드 발송 (EmailVerificationService 호출)
        emailVerificationService.sendPasswordResetCode(userEntity.getEmail());
    }

    /**
     * (Auth) 비밀번호 재설정 [2]: 코드 검증
     */
    public void verifyPasswordResetCode(PasswordResetVerifyCodeRequest request) {
        // (EmailVerificationService가 Auth DB를 보지 않으므로, 유저 존재 유무는 생략)
        emailVerificationService.verifyPasswordResetCode(request.getEmail(), request.getCode());
    }

    /**
     * (Auth) 비밀번호 재설정 [3]: 비밀번호 변경
     */
    @Transactional
    public void resetPassword(PasswordResetChangeRequest request) {
        // 1. 새 비밀번호 확인
        validatePasswordMatch(request.getNewPassword(), request.getPasswordConfirm());

        // 2. (Auth) Redis의 '검증 완료' 상태 확인
        if (!emailVerificationService.isPasswordResetVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_CODE_EXPIRED); // (적절한 ErrorCode)
        }

        // 3. (Auth) 유저 조회 (email 기준)
        AuthUserEntity user = authUserRepository.findByEmailAndProvider(request.getEmail(), UserProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 새 비밀번호 암호화 및 저장
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        authUserRepository.save(user);

        // 5. (Auth) 사용 완료된 '검증 완료' 상태 삭제
        emailVerificationService.deletePasswordResetState(request.getEmail());
    }


    // 마이페이지 > 회원 정보 변경 > 비밀번호 변경
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        // 1. (Auth) 유저 조회
        AuthUserEntity user = authUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. (Auth) 기존 비밀번호 검증
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            // '비밀번호 불일치' 코드를 재사용
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 3. (Auth) 새 비밀번호 일치 검증 (기존 메서드 재사용)
        validatePasswordMatch(request.getNewPassword(), request.getNewPasswordConfirm());

        // 4. (Auth) 새 비밀번호 암호화 및 저장
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        authUserRepository.save(user);
    }

    // == 내부 헬퍼 메서드 == //
    public UserDto getUserDetailsByEmail(String email) {
        AuthUserEntity authUser = authUserRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserDto.of(authUser.getUserId(), authUser.getEmail(), authUser.getPassword(), authUser.getRole());
    }

    public UserDto getUserDetailsById(Long userId) {
        AuthUserEntity authUser = authUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserDto.of(authUser.getUserId(), authUser.getEmail(), authUser.getPassword(), authUser.getRole());
    }
}