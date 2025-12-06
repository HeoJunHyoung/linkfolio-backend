package com.example.portfolioservice.scheduler;

import com.example.portfolioservice.repository.PortfolioRepository;
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
public class PortfolioBatchScheduler {

    private final PortfolioRepository portfolioRepository;
    private final StringRedisTemplate redisTemplate;

    // View Count Keys
    private static final String VIEW_BACKUP_KEY = "portfolio:views";
    private static final String VIEW_SYNC_KEY = "portfolio:views:sync";

    // Like Count Keys
    private static final String LIKE_BATCH_KEY = "portfolio:likes:delta";
    private static final String LIKE_SYNC_KEY = "portfolio:likes:sync";

    @Scheduled(fixedRate = 180000) // 3분마다 수행
    public void syncCountsAndCalculateScore() {
        log.info("Batch Scheduler Started");

        // 1. 조회수 동기화
        syncViewCounts();

        // 2. 좋아요 수 동기화
        syncLikeCounts();

        // 3. 인기 점수 재계산
        updatePopularityScoresInDb();

        log.info("Batch Scheduler Finished");
    }

    // 조회수 동기화 로직
    private void syncViewCounts() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(VIEW_BACKUP_KEY))) {
            redisTemplate.rename(VIEW_BACKUP_KEY, VIEW_SYNC_KEY);
            Map<Object, Object> viewCounts = redisTemplate.opsForHash().entries(VIEW_SYNC_KEY);

            if (!viewCounts.isEmpty()) {
                for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
                    try {
                        Long portfolioId = Long.parseLong((String) entry.getKey());
                        Long count = Long.parseLong((String) entry.getValue());
                        updateViewCountInDb(portfolioId, count);
                    } catch (Exception e) {
                        log.error("조회수 배치 처리 실패 ID: {}", entry.getKey());
                    }
                }
            }
            redisTemplate.delete(VIEW_SYNC_KEY);
            log.info("Synced {} portfolios view counts.", viewCounts.size());
        }
    }

    // 좋아요 수 동기화 로직
    private void syncLikeCounts() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LIKE_BATCH_KEY))) {
            // Atomic Rename: 들어오는 요청 유실 방지
            redisTemplate.rename(LIKE_BATCH_KEY, LIKE_SYNC_KEY);

            // 데이터 읽기
            Map<Object, Object> likeDeltas = redisTemplate.opsForHash().entries(LIKE_SYNC_KEY);

            if (!likeDeltas.isEmpty()) {
                for (Map.Entry<Object, Object> entry : likeDeltas.entrySet()) {
                    try {
                        Long portfolioId = Long.parseLong((String) entry.getKey());
                        Long delta = Long.parseLong((String) entry.getValue());

                        // 변화량이 0이 아닐 때만 DB 업데이트 (부하 절약)
                        if (delta != 0) {
                            updateLikeCountInDb(portfolioId, delta);
                        }
                    } catch (Exception e) {
                        log.error("좋아요 배치 처리 실패 ID: {}", entry.getKey(), e);
                    }
                }
            }
            // 처리 완료된 임시 키 삭제
            redisTemplate.delete(LIKE_SYNC_KEY);
            log.info("Synced {} portfolios like counts.", likeDeltas.size());
        }
    }

    @Transactional
    public void updateViewCountInDb(Long portfolioId, Long count) {
        portfolioRepository.incrementViewCount(portfolioId, count);
    }

    @Transactional
    public void updateLikeCountInDb(Long portfolioId, Long delta) {
        portfolioRepository.updateLikeCount(portfolioId, delta);
    }

    @Transactional
    public void updatePopularityScoresInDb() {
        portfolioRepository.updateAllPopularityScores();
    }
}