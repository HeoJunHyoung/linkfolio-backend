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
 * 자체 로그인(Local Login) 시 사용자의 이메일(username)을 기반으로 사용자 정보를 조회.
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

        // Spring Security 인증 객체(AuthUser) 반환
        return AuthUser.from(authUserEntity);
    }
}
