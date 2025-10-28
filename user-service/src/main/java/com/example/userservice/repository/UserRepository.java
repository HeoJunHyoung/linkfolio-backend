package com.example.userservice.repository;

import com.example.userservice.entity.UserEntity;
import com.example.userservice.entity.UserProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    @Query("select u from UserEntity u where u.email = :email")
    Optional<UserEntity> findUserDetailsByEmail(@Param("email") String email);

    Optional<UserEntity> findByProviderAndProviderId(UserProvider provider, String providerId);

    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);

    // 아이디 찾기용
    Optional<UserEntity> findByNameAndEmailAndProvider(String name, String email, UserProvider provider);
}
