package com.example.userservice.service;

import com.example.userservice.dto.*;
import com.example.userservice.entity.UserEntity;
import com.example.userservice.entity.UserProvider;
import com.example.userservice.exception.BusinessException;
import com.example.userservice.exception.ErrorCode;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.util.NicknameGenerator;
import com.example.userservice.util.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final NicknameGenerator nicknameGenerator;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;

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
                nicknameGenerator.generateUniqueNickname(),
                request.getUsername(),
                request.getName(),
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
    public void findId(FindIdRequest request) {
        // 1. 이름, 이메일, LOCAL 계정 여부로 유저 조회
        UserEntity user = userRepository.findByNameAndEmailAndProvider(
                request.getName(),
                request.getEmail(),
                UserProvider.LOCAL
        ).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_NAME_AND_EMAIL));

        // 2. 이메일로 아이디(username) 발송
        emailService.sendUsername(user.getEmail(), user.getUsername());
    }


    // 비밀번호 재설정 1: 코드 발송
    @Transactional(readOnly = true)
    public void sendPasswordResetCode(PasswordResetSendCodeRequest request) {
        // 1. 아이디(username)로 LOCAL 유저 조회
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .filter(u -> u.getProvider() == UserProvider.LOCAL) // 소셜 유저 제외
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 요청된 이메일과 DB의 이메일이 일치하는지 확인
        if (!user.getEmail().equals(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND); // 이메일 불일치 시에도 동일한 에러
        }

        // 3. 인증 코드 발송 (EmailVerificationService 호출)
        emailVerificationService.sendPasswordResetCode(user.getUsername(), user.getEmail());
    }

    // 비밀번호 재설정 2: 확인 및 변경
    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        // 1. 새 비밀번호 확인
        validatePasswordMatch(request.getNewPassword(), request.getPasswordConfirm());

        // 2. Redis의 코드 검증 (EmailVerificationService 호출)
        emailVerificationService.verifyPasswordResetCode(request.getUsername(), request.getCode());

        // 3. 유저 조회
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 새 비밀번호 암호화 및 저장 (dirty checking)
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user); // UserEntity에 updatePassword 메서드 필요

        // 5. 사용 완료된 코드 삭제 (EmailVerificationService 호출)
        emailVerificationService.deletePasswordResetCode(request.getUsername());
    }



    // =====================
    // 서비스 내부 헬퍼 메서드
    // =====================

    public void validateUsernameDuplicate(String username) {
        if (userRepository.existsByUsername(username)) {
            // (ErrorCode에 USERNAME_DUPLICATION 추가 필요)
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATION);
        }
    }

    private void validatePasswordMatch(String password, String passwordConfirm) {
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
