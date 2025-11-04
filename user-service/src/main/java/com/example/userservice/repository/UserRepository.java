package com.example.userservice.repository;

import com.example.userservice.entity.UserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserProfileEntity, Long> {

}
