package com.example.communityservice.controller;

import com.example.communityservice.entity.PostEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/community/test")
@RequiredArgsConstructor
// @Profile({"dev", "test"}) // 배포 시 주석 해제 권장
public class TestSetupController {

    private final PostRepository postRepository;

    /**
     * 대량 게시글 생성 (Bulk Insert)
     * 요청 바디: { "userId": 1, "count": 100 }
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<Void> setupPosts(@RequestBody Map<String, Object> request) {
        // 1. 파라미터 파싱
        Long userId = ((Number) request.get("userId")).longValue();
        int count = (int) request.get("count");

        log.info("게시글 더미 데이터 생성 시작: userId={}, count={}", userId, count);

        // 2. 데이터 생성
        List<PostEntity> posts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            posts.add(PostEntity.builder()
                    .userId(userId)
                    .title("성능 테스트용 게시글 " + i)
                    .content("이 게시글은 성능 테스트를 위해 생성된 더미 데이터입니다. ".repeat(5)) // 내용 불리기
                    .category(PostCategory.INFO)
                    .build());
        }

        // 3. 일괄 저장 (Batch Insert 효과)
        postRepository.saveAll(posts);

        log.info("게시글 {}개 생성 완료", count);
        return ResponseEntity.ok().build();
    }
}