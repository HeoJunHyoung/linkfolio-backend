package com.example.authservice.service;

import com.example.authservice.dto.AuthUser;
import com.example.authservice.entity.AuthUserEntity;
import com.example.authservice.entity.enumerate.UserProvider;
import com.example.authservice.exception.BusinessException;
import com.example.authservice.exception.ErrorCode;
import com.example.authservice.repository.AuthUserRepository;
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

        // Spring Security 인증 객체(AuthUser) 반환
        return AuthUser.from(authUserEntity);
    }
}
