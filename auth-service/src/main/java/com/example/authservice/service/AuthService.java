package com.example.authservice.service;

import com.example.authservice.client.UserServiceClient;
import com.example.authservice.dto.request.*;
import com.example.authservice.dto.response.UserResponseDto;
import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.entity.UserProvider;
import com.example.authservice.exception.BusinessException;
import com.example.authservice.exception.ErrorCode;
import com.example.authservice.repository.AuthUserRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final UserServiceClient userServiceClient;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

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
        if (authUserRepository.existsByEmail(request.getEmail())) {
            // (이메일 인증 시 Feign으로 user-db도 검사했으므로, auth-db만 검사해도 됨)
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
        }

        // 5. (User) 프로필 생성 요청 (User DB - Feign)
        UserResponseDto userProfile;
        try {
            // Feign 호출 시 DTO 변환
            userProfile = userServiceClient.createUserProfile(request).getBody();
            if (userProfile == null || userProfile.getUserId() == null) {
                log.error("UserService 프로필 생성 후 응답이 null임");
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } catch (FeignException e) {
            // (user-service에서 발생한 예외. (e.g. EMAIL_DUPLICATE))
            log.error("UserService 프로필 생성 실패 (Feign): {}", e.getMessage());
            // (이미 user-db에 이메일이 있는 경우 - email-verification에서 놓친 경우)
            if (e.status() == HttpStatus.CONFLICT.value()) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("UserService 프로필 생성 요청 중 알 수 없는 오류: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 6. (Auth) 인증 정보 생성 (Auth DB)
        AuthUserEntity authUser = AuthUserEntity.ofLocal(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getUsername()
        );

        // [수정] 5번 Feign 호출이 성공했으므로, user-service의 UserEntity.userId와
        // auth-service의 AuthUserEntity.userId를 동기화해야 함.
        // -> AuthUserEntity.java의 @Id @GeneratedValue를 제거하고,
        // -> UserEntity.java의 @Id @GeneratedValue를 유지한 뒤,
        // -> userProfile.getUserId()를 받아서 AuthUserEntity의 PK(userId)로 써야 함.
        // (위 AuthUserEntity.java, AuthUserRepository.java 코드 수정 필요)

        // --- (수정된 Entity/Repo 가정 하) ---
        // (AuthUserEntity.java)
        // @Id // @GeneratedValue(strategy = GenerationType.IDENTITY) <-- 제거
        // @Column(name = "user_id")
        // private Long userId;
        //
        // public AuthUserEntity(Long userId, String email, ...) { this.userId = userId; ... }
        // public static AuthUserEntity ofLocal(Long userId, String email, ...) {
        //    return new AuthUserEntity(userId, email, ...);
        // }
        // (AuthUserRepository.java)
        // public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> { ... }

        /*
        AuthUserEntity authUser = AuthUserEntity.ofLocal(
                userProfile.getUserId(), // [수정]
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getUsername()
        );
        authUserRepository.save(authUser);
        */

        // [중요] AuthUserEntity의 PK(userId)를 userProfile.getUserId()로 맞추는 것은
        // @MapsId를 사용하거나, AuthUserEntity의 @Id에 @GeneratedValue를 빼고
        // 수동으로 userProfile.getUserId()를 할당해야 함.
        // -> 여기서는 5, 6번이 분리된 동기식 Feign 호출의 한계로 두고,
        // -> UserEntity와 AuthUserEntity가 각자 Auto-Increment ID를 갖되,
        // -> AuthUserEntity가 userProfile.getUserId()를 '외래 키'로 갖도록 수정.

        // (AuthUserEntity.java에 다음 필드 추가)
        // @Column(name = "user_profile_id", unique = true)
        // private Long userProfileId;
        //
        // (AuthUserEntity 생성자 수정)
        // public AuthUserEntity(..., Long userProfileId) { this.userProfileId = userProfileId; }

        // (다시 AuthSerivce.java)
        AuthUserEntity authUser = AuthUserEntity.ofLocal(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getUsername()
                // , userProfile.getUserId() // 외래 키 설정
        );
        authUserRepository.save(authUser);

        // [분산 트랜잭션 위험]
        // 6번(Auth DB 저장)이 실패하면, 5번(User DB 프로필)은 롤백되지 않아 Orphaned User가 발생.

        // 7. (Auth) 회원가입 완료 후, Redis의 "인증 완료" 상태 삭제
        emailVerificationService.deleteVerifiedEmailStatus(request.getEmail());
    }

    /**
     * 아이디(username) 찾기
     */
    @Transactional(readOnly = true)
    public String findUsername(FindUsernameRequest request) {

        // 1. (User) 이름+이메일로 프로필 조회 (User DB - Feign)
        UserResponseDto userProfile;
        try {
            userProfile = userServiceClient.findUserByProfile(request).getBody();
            if (userProfile == null || userProfile.getUserId() == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND_BY_NAME_AND_EMAIL);
            }
        } catch (FeignException e) {
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND_BY_NAME_AND_EMAIL);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        // 2. (Auth) Email과 LOCAL Provider로 인증 정보 조회 (Auth DB)
        // (UserEntity.userId와 AuthUserEntity.userId가 다를 수 있으므로 Email로 조회)
        AuthUserEntity authUser = authUserRepository.findByEmailAndProvider(
                request.getEmail(),
                UserProvider.LOCAL
        ).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_NAME_AND_EMAIL));

        // 3. (Auth) ID(username) 반환
        return authUser.getUsername();
    }

    // (Auth) ID 중복 검사
    public void validateUsernameDuplicate(String username) {
        if (authUserRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATION);
        }
    }

    // (Auth) 비밀번호 일치 검사
    public void validatePasswordMatch(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    // (Auth) 비밀번호 재설정 [1]: 코드 발송
    @Transactional(readOnly = true)
    public void sendPasswordResetCode(PasswordResetSendCodeRequest request) {
        // 1. (Auth) 이메일로 LOCAL 유저 조회
        AuthUserEntity userEntity = authUserRepository.findByEmailAndProvider(request.getEmail(), UserProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 인증 코드 발송 (EmailVerificationService 호출)
        emailVerificationService.sendPasswordResetCode(userEntity.getEmail());
    }

    // (Auth) 비밀번호 재설정 [2]: 코드 검증
    public void verifyPasswordResetCode(PasswordResetVerifyCodeRequest request) {
        // (EmailVerificationService가 Auth DB를 보지 않으므로, 유저 존재 유무는 생략)
        emailVerificationService.verifyPasswordResetCode(request.getEmail(), request.getCode());
    }

    // (Auth) 비밀번호 재설정 [3]: 비밀번호 변경
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
}