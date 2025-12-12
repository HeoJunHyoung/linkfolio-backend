package com.example.authservice.service;

import com.example.authservice.dto.AuthUser;
import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.entity.enumerate.AuthStatus;
import com.example.authservice.exception.ErrorCode;
import com.example.authservice.repository.AuthUserRepository;
import com.example.commonmodule.entity.enumerate.UserProvider;
import com.example.commonmodule.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security의 UserDetailsService 인터페이스를 구현.
 * ㄴ 1. ID가 맞는지 확인하기 위해 CustomUserDetailsService의 loadUserByUsername을 호출
 * ㄴ 2. DB에서 유저 정보를 가져와 AuthUser(UserDetails) 객체를 리턴
 * ㄴ 3. AuthUser 비밀번호랑 사용자가 입력한 비밀번호가 일치하는지 Spring Security 내부적으로 검증
 * ㄴ 4. 인증 성공 핸들러(LocalLoginSuccessHandler) 실행
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthUserRepository authUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AuthUserEntity authUserEntity = authUserRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 'username'으로 로그인 하는것은 LOCAL 계정만 가능하도록 설정
        if (authUserEntity.getProvider() != UserProvider.LOCAL) {
            log.warn("Social user ({}) attempted local login with ID: {}", authUserEntity.getEmail(), username);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND); // 소셜 유저가 ID/PW로 로그인 시도 시, 계정 정보 숨김
        }

        // SAGA 트랜잭션 상태 확인
        // ㄴ PENDING (회원가입 중) 또는 CANCELLED (회원가입 실패/롤백) 상태인 경우 로그인 차단
        if (authUserEntity.getStatus() != AuthStatus.COMPLETED) {
            log.warn("Login attempt for non-completed account. Username: {}, Status: {}",
                    username, authUserEntity.getStatus());
            // 계정 상태에 대한 구체적인 정보를 노출하지 않기 위해 USER_NOT_FOUND 반환
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Spring Security 인증 객체(AuthUser) 반환: LocalLoginSuccessHandler 호출
        return AuthUser.from(authUserEntity);
    }
}
