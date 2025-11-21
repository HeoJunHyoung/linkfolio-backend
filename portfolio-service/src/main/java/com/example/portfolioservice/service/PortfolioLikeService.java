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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PortfolioLikeService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioLikeRepository portfolioLikeRepository;
    private final PortfolioMapper portfolioMapper;

    /**
     * 포트폴리오 관심 추가
     */
    public void addLike(Long authUserId, Long portfolioId) {
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 발행되지 않은 포트폴리오는 숨김 처리
        if (!portfolio.isPublished()) {
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        // 중복 좋아요 방지
        if (portfolioLikeRepository.existsByLikerIdAndPortfolio(authUserId, portfolio)) {
            log.warn("이미 관심 추가된 포트폴리오입니다. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
            return;
        }

        // 1. PortfolioLike 엔티티 생성 및 저장
        PortfolioLikeEntity portfolioLike = PortfolioLikeEntity.of(authUserId, portfolio);
        portfolioLikeRepository.save(portfolioLike);

        // 2. Portfolio 엔티티의 likeCount만 증가
        portfolio.increaseLikeCount();

        log.info("관심 추가 완료. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
    }

    /**
     * 포트폴리오 관심 취소
     */
    public void removeLike(Long authUserId, Long portfolioId) {
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 삭제할 PortfolioLike 엔티티 조회
        PortfolioLikeEntity portfolioLike = portfolioLikeRepository.findByLikerIdAndPortfolio(authUserId, portfolio)
                .orElse(null);

        if (portfolioLike == null) {
            log.warn("관심 추가되지 않은 포트폴리오입니다. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
            return; // 멱등성
        }

        // 1. PortfolioLike 엔티티 제거
        portfolioLikeRepository.delete(portfolioLike);

        // 2. portfolio 엔터티의 likeCount 감소
        portfolio.decreaseLikeCount();

        log.info("관심 취소 완료. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
    }

    /**
     * 내 관심 포트폴리오 목록 조회
     */
    @Transactional(readOnly = true)
    public Slice<PortfolioCardResponse> getMyLikedPortfolios(Long authUserId, String position, Pageable pageable) {
        return portfolioLikeRepository.searchMyLikedPortfolios(authUserId, position, pageable);
    }
}