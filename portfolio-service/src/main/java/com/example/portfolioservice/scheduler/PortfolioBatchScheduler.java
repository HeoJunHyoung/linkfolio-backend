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

    private static final String VIEW_KEY = "portfolio:views";
    private static final String VIEW_KEY_SYNC = "portfolio:views:sync"; // 작업 중 사용할 임시 키

    @Scheduled(fixedRate = 600000) // 10분
    public void syncViewCountsAndCalculateScore() {
        log.info("Batch Scheduler Started");

        // 1. Redis Key Rename (Atomic)
        // ㄴ 작업 도중 들어오는 새로운 조회수 요청이 유실되지 않도록 현재 쌓인 데이터를 임시 키(sync)로 옮기고, 원본 키 비워둠
        if (Boolean.TRUE.equals(redisTemplate.hasKey(VIEW_KEY))) {
            redisTemplate.rename(VIEW_KEY, VIEW_KEY_SYNC);

            // 2. Hash 데이터 통째로 가져오기 (1회 네트워크 호출)
            Map<Object, Object> viewCounts = redisTemplate.opsForHash().entries(VIEW_KEY_SYNC);

            if (!viewCounts.isEmpty()) {
                // 3. DB Bulk Update
                for (Map.Entry<Object, Object> entry : viewCounts.entrySet()) {
                    Long portfolioId = Long.parseLong((String) entry.getKey());
                    Long count = Long.parseLong((String) entry.getValue());

                    // 개별 트랜잭션으로 처리하여 Long Transaction 방지
                    updateViewCountInDb(portfolioId, count);
                }
            }

            // 4. 임시 키 삭제
            redisTemplate.delete(VIEW_KEY_SYNC);
            log.info("Synced {} portfolios view counts.", viewCounts.size());
        }

        // 5. 점수 재계산 (DB Native Query로 한방에 처리)
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