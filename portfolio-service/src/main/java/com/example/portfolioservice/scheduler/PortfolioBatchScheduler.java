package com.example.portfolioservice.scheduler;

import com.example.portfolioservice.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class PortfolioBatchScheduler {

    private final PortfolioRepository portfolioRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String VIEW_BACKUP_KEY = "portfolio:views";
    private static final String VIEW_SYNC_KEY = "portfolio:views:sync";

    @Scheduled(fixedRate = 600000) // 10분
    public void syncViewCountsAndCalculateScore() {
        log.info("Batch Scheduler Started");

        // 1. 조회수 DB 반영 (Write-Back)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(VIEW_BACKUP_KEY))) {
            redisTemplate.rename(VIEW_BACKUP_KEY, VIEW_SYNC_KEY);
            Map<Object, Object> viewCounts = redisTemplate.opsForHash().entries(VIEW_SYNC_KEY);

            if (!viewCounts.isEmpty()) {
                for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
                    Long portfolioId = Long.parseLong((String) entry.getKey());
                    Long count = Long.parseLong((String) entry.getValue());

                    // DB 증가 (누적된 조회수만큼)
                    updateViewCountInDb(portfolioId, count);
                }
            }
            redisTemplate.delete(VIEW_SYNC_KEY);
            log.info("Synced {} portfolios view counts.", viewCounts.size());
        }

        // 2. 인기 점수 재계산
        updatePopularityScoresInDb();

        log.info("Batch Scheduler Finished");
    }

    // 트랜잭션 분리를 위한 별도 메서드
    @Transactional
    public void updateViewCountInDb(Long portfolioId, Long count) {
        portfolioRepository.incrementViewCount(portfolioId, count);
    }

    @Transactional
    public void updatePopularityScoresInDb() {
        portfolioRepository.updateAllPopularityScores();
    }
}