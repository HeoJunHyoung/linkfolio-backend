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

    // Keys
    private static final String VIEW_BATCH_KEY = "post:views";
    private static final String VIEW_SYNC_KEY = "post:views:sync";

    private static final String BOOKMARK_BATCH_KEY = "post:bookmarks:delta";
    private static final String BOOKMARK_SYNC_KEY = "post:bookmarks:sync";

    private static final String COMMENT_BATCH_KEY = "post:comments:delta";
    private static final String COMMENT_SYNC_KEY = "post:comments:sync";

    @Scheduled(fixedRate = 180000, initialDelay = 30000) // 3분마다 실행 (Portfolio와 겹치지 않게 30초 딜레이)
    @Transactional
    public void syncCounts() {
        log.info("[Scheduler] 커뮤니티 통계 데이터 DB 동기화 시작");

        try {
            syncViewCounts();
            syncBookmarkCounts();
            syncCommentCounts();
        } catch (Exception e) {
            log.error("배치 동기화 중 예상치 못한 오류 발생", e);
        }

        log.info("[Scheduler] 동기화 작업 완료");
    }

    // 1. 조회수 동기화
    private void syncViewCounts() {
        processBatch(VIEW_BATCH_KEY, VIEW_SYNC_KEY, (postId, count) -> postRepository.incrementViewCount(postId, count));
    }

    // 2. 북마크 수 동기화
    private void syncBookmarkCounts() {
        processBatch(BOOKMARK_BATCH_KEY, BOOKMARK_SYNC_KEY, (postId, delta) -> {
            if (delta != 0) postRepository.updateBookmarkCount(postId, delta);
        });
    }

    // 3. 댓글 수 동기화
    private void syncCommentCounts() {
        processBatch(COMMENT_BATCH_KEY, COMMENT_SYNC_KEY, (postId, delta) -> {
            if (delta != 0) postRepository.updateCommentCount(postId, delta);
        });
    }

    // 공통 배치 처리 로직
    private void processBatch(String batchKey, String syncKey, BatchUpdateAction action) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(batchKey))) {
            redisTemplate.rename(batchKey, syncKey);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(syncKey);

            if (!entries.isEmpty()) {
                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    try {
                        Long postId = Long.parseLong((String) entry.getKey());
                        Long value = Long.parseLong((String) entry.getValue());
                        action.update(postId, value); // DB 업데이트 수행
                    } catch (Exception e) {
                        log.error("배치 처리 실패 - Key: {}, PostId: {}", batchKey, entry.getKey(), e);
                    }
                }
            }
            redisTemplate.delete(syncKey);
        }
    }

    // 함수형 인터페이스 (내부에서만 사용)
    @FunctionalInterface
    interface BatchUpdateAction {
        void update(Long postId, Long value);
    }
}