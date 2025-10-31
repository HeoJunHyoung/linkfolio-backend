package com.example.userservice.repository;

import com.example.userservice.entity.UserProfileEntity;
import com.example.userservice.entity.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserProfileEntity, Long> {

    @Query("select u from UserProfileEntity u where u.email = :email")
    Optional<UserProfileEntity> findUserDetailsByEmail(@Param("email") String email);

    Optional<UserProfileEntity> findByProviderAndProviderId(UserProvider provider, String providerId);

    Optional<UserProfileEntity> findByUsername(String username);
    boolean existsByUsername(String username);

    // 아이디 찾기용
    Optional<UserProfileEntity> findByNameAndEmailAndProvider(String name, String email, UserProvider provider);
}
