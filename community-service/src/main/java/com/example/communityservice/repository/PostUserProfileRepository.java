package com.example.communityservice.repository;

import com.example.communityservice.entity.PostUserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostUserProfileRepository extends JpaRepository<PostUserProfileEntity, Long> {
}