package com.example.portfolioservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.entity.PortfolioLikeEntity;
import com.example.portfolioservice.exception.ErrorCode;
import com.example.portfolioservice.repository.PortfolioLikeRepository;
import com.example.portfolioservice.repository.PortfolioRepository;
import com.example.portfolioservice.util.PortfolioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PortfolioLikeService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioLikeRepository portfolioLikeRepository;
    private final StringRedisTemplate redisTemplate;

    // 실시간 조회용 (화면에 보여지는 값)
    private static final String STATS_KEY_PREFIX = "portfolio:stats:";
    // 배치 동기화용 (DB에 반영할 증감분)
    private static final String LIKE_BATCH_KEY = "portfolio:likes:delta";

    /**
     * 포트폴리오 북마크 추가
     */
    public void addLike(Long authUserId, Long portfolioId) {
        // 1. 검증 (Proxy 조회로 쿼리 절약)
        PortfolioEntity portfolio = portfolioRepository.getReferenceById(portfolioId);

        // 중복 체크 (DB 조회는 필요하지만, Parent Row Lock은 걸리지 않음)
        if (portfolioLikeRepository.existsByLikerIdAndPortfolio(authUserId, portfolio)) {
            return;
        }

        // 2. DB 반영 (자식 테이블 Insert만 수행 -> Parent Lock 없음)
        portfolioLikeRepository.save(PortfolioLikeEntity.of(authUserId, portfolio));

        // 3. Redis 반영
        // A. 실시간 조회용 값 증가 (+1)
        redisTemplate.opsForHash().increment(STATS_KEY_PREFIX + portfolioId, "likeCount", 1L);
        // B. 배치 동기화용 Delta 값 증가 (+1)
        redisTemplate.opsForHash().increment(LIKE_BATCH_KEY, String.valueOf(portfolioId), 1L);
    }

    /**
     * 포트폴리오 북마크 취소 (DB + Redis 동시 업데이트)
     */
    public void removeLike(Long authUserId, Long portfolioId) {
        PortfolioEntity portfolio = portfolioRepository.getReferenceById(portfolioId);

        PortfolioLikeEntity portfolioLike = portfolioLikeRepository.findByLikerIdAndPortfolio(authUserId, portfolio)
                .orElse(null);

        if (portfolioLike == null) return;

        // 2. DB 반영 (자식 테이블 Delete만 수행)
        portfolioLikeRepository.delete(portfolioLike);

        // 3. Redis 반영
        // A. 실시간 조회용 값 감소 (-1)
        redisTemplate.opsForHash().increment(STATS_KEY_PREFIX + portfolioId, "likeCount", -1L);
        // B. 배치 동기화용 Delta 값 감소 (-1)
        redisTemplate.opsForHash().increment(LIKE_BATCH_KEY, String.valueOf(portfolioId), -1L);
    }

    /**
     * 내 관심 포트폴리오 목록 조회
     */
    @Transactional(readOnly = true)
    public Slice<PortfolioCardResponse> getMyLikedPortfolios(Long authUserId, String position, Pageable pageable) {
        return portfolioLikeRepository.searchMyLikedPortfolios(authUserId, position, pageable);
    }
}