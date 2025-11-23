package com.example.communityservice.repository;

import com.example.communityservice.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, Long>, PostRepositoryCustom {
}