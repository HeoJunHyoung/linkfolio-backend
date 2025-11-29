package com.example.communityservice.scheduler;

import com.example.communityservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostBatchScheduler {

    private final PostRepository postRepository;
    private final StringRedisTemplate redisTemplate;

    // Redis Key Constants
    private static final String VIEW_BATCH_KEY = "post:views";
    private static final String VIEW_SYNC_KEY = "post:views:sync";

    @Scheduled(fixedRate = 600000) // 10분마다 실행
    public void syncViewCounts() {
        // 1. Redis에 데이터가 있는지 확인
        if (Boolean.TRUE.equals(redisTemplate.hasKey(VIEW_BATCH_KEY))) {
            log.info("[Scheduler] 게시글 조회수 DB 동기화 시작");

            // 2. Key Rename (Atomic 처리: 동기화 중 들어오는 데이터 유실 방지)
            redisTemplate.rename(VIEW_BATCH_KEY, VIEW_SYNC_KEY);

            // 3. 데이터 읽기
            Map<Object, Object> viewCounts = redisTemplate.opsForHash().entries(VIEW_SYNC_KEY);

            if (!viewCounts.isEmpty()) {
                // 4. DB 일괄 업데이트
                for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
                    try {
                        Long postId = Long.parseLong((String) entry.getKey());
                        Long count = Long.parseLong((String) entry.getValue());

                        updateViewCountInDb(postId, count);
                    } catch (Exception e) {
                        log.error("조회수 반영 실패 - postId: {}", entry.getKey(), e);
                    }
                }
            }

            // 5. 임시 키 삭제
            redisTemplate.delete(VIEW_SYNC_KEY);
            log.info("[Scheduler] 총 {}개 게시글의 조회수 반영 완료", viewCounts.size());
        }
    }

    // 트랜잭션 분리를 위해 별도 메서드로 추출
    @Transactional
    public void updateViewCountInDb(Long postId, Long count) {
        postRepository.incrementViewCount(postId, count);
    }
}