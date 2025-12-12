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

    // Keys
    private static final String VIEW_BACKUP_KEY = "portfolio:views";
    private static final String VIEW_SYNC_KEY = "portfolio:views:sync";
    private static final String LIKE_BATCH_KEY = "portfolio:likes:delta";
    private static final String LIKE_SYNC_KEY = "portfolio:likes:sync";

    @Scheduled(fixedRate = 180000) // 3분마다 수행
    @Transactional
    public void syncCountsAndCalculateScore() {
        log.info("Batch Scheduler Started");

        syncViewCounts();
        syncLikeCounts();
        updatePopularityScoresInDb();

        log.info("Batch Scheduler Finished");
    }

    // 조회수 동기화 로직
    private void syncViewCounts() {
        // [추가 팁] 분산 환경(K8s) 고려: rename 전 키 존재 확인은 원자적이지 않음.
        // rename 자체가 실패하면 Exception이 발생하므로 try-catch로 감싸는 것이 안전함.
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(VIEW_BACKUP_KEY))) {
                redisTemplate.rename(VIEW_BACKUP_KEY, VIEW_SYNC_KEY);

                Map<Object, Object> viewCounts = redisTemplate.opsForHash().entries(VIEW_SYNC_KEY);
                if (!viewCounts.isEmpty()) {
                    for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
                        try {
                            Long portfolioId = Long.parseLong((String) entry.getKey());
                            Long count = Long.parseLong((String) entry.getValue());
                            // 여기서는 이제 트랜잭션이 유지됨
                            updateViewCountInDb(portfolioId, count);
                        } catch (Exception e) {
                            log.error("조회수 배치 처리 실패 ID: {}", entry.getKey(), e);
                        }
                    }
                }
                redisTemplate.delete(VIEW_SYNC_KEY);
                log.info("Synced {} portfolios view counts.", viewCounts.size());
            }
        } catch (Exception e) {
            // 키가 없거나, 다른 인스턴스가 이미 가져간 경우 무시
            log.debug("View sync skipped or failed: {}", e.getMessage());
        }
    }

    // 좋아요 수 동기화 로직
    private void syncLikeCounts() {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(LIKE_BATCH_KEY))) {
                redisTemplate.rename(LIKE_BATCH_KEY, LIKE_SYNC_KEY);

                Map<Object, Object> likeDeltas = redisTemplate.opsForHash().entries(LIKE_SYNC_KEY);
                if (!likeDeltas.isEmpty()) {
                    for (Map.Entry<Object, Object> entry : likeDeltas.entrySet()) {
                        try {
                            Long portfolioId = Long.parseLong((String) entry.getKey());
                            Long delta = Long.parseLong((String) entry.getValue());
                            if (delta != 0) {
                                updateLikeCountInDb(portfolioId, delta);
                            }
                        } catch (Exception e) {
                            log.error("좋아요 배치 처리 실패 ID: {}", entry.getKey(), e);
                        }
                    }
                }
                redisTemplate.delete(LIKE_SYNC_KEY);
                log.info("Synced {} portfolios like counts.", likeDeltas.size());
            }
        } catch (Exception e) {
            log.debug("Like sync skipped or failed: {}", e.getMessage());
        }
    }

    // 아래 메서드들의 @Transactional은 yncCountsAndCalculateScore의 트랜잭션에 합류
    private void updateViewCountInDb(Long portfolioId, Long count) {
        portfolioRepository.incrementViewCount(portfolioId, count);
    }

    private void updateLikeCountInDb(Long portfolioId, Long delta) {
        portfolioRepository.updateLikeCount(portfolioId, delta);
    }

    private void updatePopularityScoresInDb() {
        portfolioRepository.updateAllPopularityScores();
    }
}