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

        // 이미 좋아요를 눌렀는지 확인
        if (portfolioLikeRepository.existsByUserIdAndPortfolio(authUserId, portfolio)) {
            log.warn("이미 관심 추가된 포트폴리오입니다. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
            return; // 멱등성을 위해 에러 대신 정상 반환
        }

        // 1. PortfolioLike 엔티티 생성 및 저장
        PortfolioLikeEntity portfolioLike = PortfolioLikeEntity.of(authUserId, portfolio);
        portfolioLikeRepository.save(portfolioLike);

        // 2. Portfolio 엔티티에 likeCount 업데이트 (편의 메서드 사용)
        //    (JPA Dirty Checking에 의해 트랜잭션 종료 시 update 쿼리 발생)
        portfolio.addLike(portfolioLike);

        log.info("관심 추가 완료. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
    }

    /**
     * 포트폴리오 관심 취소
     */
    public void removeLike(Long authUserId, Long portfolioId) {
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 삭제할 PortfolioLike 엔티티 조회
        PortfolioLikeEntity portfolioLike = portfolioLikeRepository.findByUserIdAndPortfolio(authUserId, portfolio)
                .orElse(null);

        if (portfolioLike == null) {
            log.warn("관심 추가되지 않은 포트폴리오입니다. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
            return; // 멱등성
        }

        // 1. Portfolio 엔티티에서 likeCount 업데이트 및 연관관계 제거
        portfolio.removeLike(portfolioLike);

        // 2. PortfolioLike 엔티티 삭제
        // ㄴ portfolio.removeLike()가 리스트에서만 제거하므로, Repository를 통한 DB 삭제는 별도 수행
        portfolioLikeRepository.delete(portfolioLike);

        log.info("관심 취소 완료. UserId: {}, PortfolioId: {}", authUserId, portfolioId);
    }

    /**
     * 내 관심 포트폴리오 목록 조회
     */
    @Transactional(readOnly = true)
    public Slice<PortfolioCardResponse> getMyLikedPortfolios(Long authUserId, Pageable pageable) {
        Slice<PortfolioLikeEntity> likes = portfolioLikeRepository.findAllByUserId(authUserId, pageable);

        // PortfolioLikeEntity Slice -> PortfolioEntity Slice -> PortfolioCardResponse Slice로 변환
        return likes.map(portfolioLike -> portfolioMapper.toPortfolioCardResponse(portfolioLike.getPortfolio()));
    }
}