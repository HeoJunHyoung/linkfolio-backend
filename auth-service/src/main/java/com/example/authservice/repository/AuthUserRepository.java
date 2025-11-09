package com.example.authservice.repository;

import com.example.authservice.entity.AuthUserEntity;
import com.example.commonmodule.entity.enumerate.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

    Optional<AuthUserEntity> findByEmail(String email);

    Optional<AuthUserEntity> findByProviderAndProviderId(UserProvider provider, String providerId);

    // CustomUserDetailsService가 사용
    Optional<AuthUserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AuthUserEntity> findByEmailAndProvider(String email, UserProvider userProvider);

    // '아이디 찾기'가 사용
    Optional<AuthUserEntity> findByNameAndEmailAndProvider(String name, String email, UserProvider provider);
}