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

    private static final String STATS_KEY_PREFIX = "portfolio:stats:";

    /**
     * 포트폴리오 북마크 추가 (DB + Redis 동시 업데이트)
     */
    public void addLike(Long authUserId, Long portfolioId) {
        // 1. 검증 (Proxy 조회로 쿼리 절약 가능)
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (!portfolio.isPublished()) throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);

        if (portfolioLikeRepository.existsByLikerIdAndPortfolio(authUserId, portfolio)) {
            return;
        }

        // 2. DB 반영 (안전성 보장)
        portfolioLikeRepository.save(PortfolioLikeEntity.of(authUserId, portfolio));
        portfolio.increaseLikeCount();

        // 3. Redis 반영 (실시간성 보장)
        // ㄴ 전체 캐시를 삭제하는게 아니라, '숫자'만 1 올림 -> Cache Miss 없음
        redisTemplate.opsForHash().increment(STATS_KEY_PREFIX + portfolioId, "likeCount", 1L);
    }

    /**
     * 포트폴리오 북마크 취소 (DB + Redis 동시 업데이트)
     */
    public void removeLike(Long authUserId, Long portfolioId) {
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        PortfolioLikeEntity portfolioLike = portfolioLikeRepository.findByLikerIdAndPortfolio(authUserId, portfolio)
                .orElse(null);

        if (portfolioLike == null) return;

        // 2. DB 반영
        portfolioLikeRepository.delete(portfolioLike);
        portfolio.decreaseLikeCount();

        // 3. Redis 반영
        redisTemplate.opsForHash().increment(STATS_KEY_PREFIX + portfolioId, "likeCount", -1L);
    }

    /**
     * 내 관심 포트폴리오 목록 조회
     */
    @Transactional(readOnly = true)
    public Slice<PortfolioCardResponse> getMyLikedPortfolios(Long authUserId, String position, Pageable pageable) {
        return portfolioLikeRepository.searchMyLikedPortfolios(authUserId, position, pageable);
    }
}