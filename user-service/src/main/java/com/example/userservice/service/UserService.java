package com.example.userservice.service;

import com.example.userservice.dto.*;
import com.example.userservice.dto.request.*;
import com.example.userservice.dto.response.UserResponse;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.entity.UserProvider;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // 회원가입
    @Transactional
    public void signUp(UserSignUpRequest request) {

        // 1. 이메일이 "인증 완료" 상태인지 확인
        if (!emailVerificationService.isEmailVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED); // (ErrorCode 추가 필요)
        }

        // 2. DB 레벨의 비밀번호 / 이메일 검증
        validatePasswordMatch(request.getPassword(), request.getPasswordConfirm());
        validateEmailDuplicate(request.getEmail());

        // 3. 'username' 중복 검증
        validateUsernameDuplicate(request.getUsername());

        // 4. 객체 생성
        UserEntity signUpUser = UserEntity.ofLocal(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getUsername(), // 실명
                request.getName(),     // 아이디(ID)
                request.getBirthdate(),
                request.getGender()
        );

        userRepository.save(signUpUser);  // 5. 객체 저장

        // 6. 회원가입 완료 후, Redis의 "인증 완료" 상태 삭제
        emailVerificationService.deleteVerifiedEmailStatus(request.getEmail());
    }


    // 회원 단일 조회
    public UserResponse getUser(Long userId) {
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(userEntity);
    }

    // 아이디 찾기
    @Transactional(readOnly = true)
    public String findUsername(FindUsernameRequest request) {
        // 1. 실명, 이메일, LOCAL 계정 여부로 유저 조회
        UserEntity user = userRepository.findByNameAndEmailAndProvider(
                request.getName(),
                request.getEmail(),
                UserProvider.LOCAL
        ).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_NAME_AND_EMAIL));

        // 2. ID(username) 반환
        return user.getUsername();
    }


    // 비밀번호 재설정 1: 코드 발송
    @Transactional(readOnly = true)
    public void sendPasswordResetCode(PasswordResetSendCodeRequest request) {
        // 1. 이메일로 LOCAL 유저 조회
        UserEntity userEntity = userRepository.findUserDetailsByEmail(request.getEmail())
                .filter(u -> u.getProvider() == UserProvider.LOCAL) // 소셜 유저 제외
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 인증 코드 발송 (EmailVerificationService 호출)
        emailVerificationService.sendPasswordResetCode(userEntity.getEmail());
    }

    // 비밀번호 재설정 2: 인증 코드 검증
    public void verifyPasswordResetCode(PasswordResetVerifyCodeRequest request) {
        emailVerificationService.verifyPasswordResetCode(request.getEmail(), request.getCode());
    }
    
    // 비밀번호 재설정 3: 비밀번호 확인 및 변경
    @Transactional
    public void resetPassword(PasswordResetChangeRequest request) { // <-- [수정] DTO 변경
        // 1. 새 비밀번호 확인
        validatePasswordMatch(request.getNewPassword(), request.getPasswordConfirm());

        // 2. Redis의 '검증 완료' 상태 확인
        if (!emailVerificationService.isPasswordResetVerified(request.getEmail())) {
            // ErrorCode 추가 필요 (e.g., PW_RESET_NOT_VERIFIED)
            throw new BusinessException(ErrorCode.PASSWORD_RESET_CODE_EXPIRED);
        }

        // 3. 유저 조회 (email 기준)
        UserEntity user = userRepository.findUserDetailsByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 새 비밀번호 암호화 및 저장
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 5. 사용 완료된 '검증 완료' 상태 삭제
        emailVerificationService.deletePasswordResetState(request.getEmail()); // <-- 메서드명 변경
    }



    // =====================
    // 서비스 내부 헬퍼 메서드
    // =====================

    public void validateUsernameDuplicate(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATION);
        }
    }

    public void validatePasswordMatch(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    public void validateEmailDuplicate(String email) {
        userRepository.findUserDetailsByEmail(email).ifPresent(user -> {
            // 이메일이 이미 존재하면, 가입 경로에 따라 다른 예외 발생
            if (user.getProvider() == UserProvider.LOCAL) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
            } else {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED_AS_SOCIAL);
            }
        });
    }

    public UserDto getUserDetailsByEmail(String email) {
        UserEntity userEntity = userRepository.findUserDetailsByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserDto(userEntity);
    }

    public UserDto getUserDetailsById(Long userId) {
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserDto(userEntity); // Mapper 이용
    }

    public UserDto getUserDetailsByUsername(String username) {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserDto(userEntity);
    }

    

}
