package com.example.portfolioservice.service;

import com.example.commonmodule.dto.security.AuthUser;
import com.example.commonmodule.exception.BusinessException;
import com.example.portfolioservice.dto.request.PortfolioRequest;
import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioDetailsResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.exception.ErrorCode;
import com.example.portfolioservice.repository.PortfolioLikeRepository;
import com.example.portfolioservice.repository.PortfolioRepository;
import com.example.portfolioservice.util.PortfolioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioLikeRepository portfolioLikeRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 내 포트폴리오 조회 (마이페이지)
     */
    @Transactional(readOnly = true)
    public PortfolioDetailsResponse getMyPortfolio(Long authUserId) {
        PortfolioEntity portfolio = portfolioRepository.findByUserId(authUserId)
                .orElseThrow(() -> {
                    log.warn("PortfolioEntity가 존재하지 않음. Kafka 이벤트 처리 지연 또는 실패. UserId: {}", authUserId);
                    return new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
                });

        // 2. DTO로 변환하여 반환
        return portfolioMapper.toPortfolioResponse(portfolio, false);
    }

    /**
     * 포트폴리오 생성/수정 (마이페이지 [등록하기] 또는 [수정하기] 버튼)
     */
    @Transactional
    public PortfolioDetailsResponse createOrUpdateMyPortfolio(Long authUserId, PortfolioRequest request) {

        PortfolioEntity portfolio = portfolioRepository.findByUserId(authUserId)
                .orElseThrow(() -> {
                    log.warn("PortfolioEntity가 존재하지 않음 (createOrUpdate). Kafka 이벤트 처리 지연 또는 실패. UserId: {}", authUserId);
                    return new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
                });

        // 2. 사용자 입력 정보(Request)로 엔티티 갱신
        //    updateUserInput 내부에서 isPublished가 'true'로 변경됨
        portfolio.updateUserInput(
                request.getPhotoUrl(),
                request.getOneLiner(),
                request.getContent(),
                request.getPosition(),
                request.getHashtags()
        );

        // 3. DB 저장 (Update) (2차 저장)
        PortfolioEntity updatedPortfolio = portfolioRepository.save(portfolio);

        return portfolioMapper.toPortfolioResponse(updatedPortfolio, false);
    }

    /**
     * 포트폴리오 카드 목록 조회 (메인 페이지 - 인증 불필요)
     */
    public Slice<PortfolioCardResponse> getPortfolioList(Pageable pageable, String position) {
        return portfolioRepository.searchPortfolioList(position, pageable);
    }

    /**
     * 포트폴리오 상세 조회 (상세보기 - 인증 불필요)
     */
    @Transactional
    public PortfolioDetailsResponse getPortfolioDetails(Long portfolioId, AuthUser authUser) {
        // Feign 호출 없이, 캐시된 DB 데이터만으로 응답
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 발행(isPublished=true)되지 않은 포트폴리오는 찾을 수 없는 것으로 처리
        if (!portfolio.isPublished()) {
            log.warn("아직 발행되지 않은 포트폴리오 접근 시도. UserId: {}", portfolioId);
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        // 1. Redis 카운트 증가
        String viewKey = "portfolio:view:" + portfolioId;
        redisTemplate.opsForValue().increment(viewKey);

        // 2. 실시간 조회수 계산 (DB 값 + Redis 값)
        Object cachedValue = redisTemplate.opsForValue().get(viewKey);
        long cachedCount = (cachedValue != null) ? Long.parseLong(String.valueOf(cachedValue)) : 0L;

        long realTimeViewCount = portfolio.getViewCount() + cachedCount;


        boolean isLiked = false;
        if (authUser != null) {
            // 로그인한 상태라면, Like 테이블을 조회하여 관심 여부 확인
            isLiked = portfolioLikeRepository.existsByLikerIdAndPortfolio(authUser.getUserId(), portfolio);
        }

        List<String> hashtags = portfolioMapper.stringToHashtagList(portfolio.getHashtags());
        return PortfolioDetailsResponse.builder()
                .userId(portfolio.getUserId())
                .name(portfolio.getName())
                .email(portfolio.getEmail())
                .birthdate(portfolio.getBirthdate())
                .gender(portfolio.getGender())
                .photoUrl(portfolio.getPhotoUrl())
                .oneLiner(portfolio.getOneLiner())
                .content(portfolio.getContent())
                .position(portfolio.getPosition())
                .hashtags(hashtags)
                .isPublished(portfolio.isPublished())
                .isLiked(isLiked)
                .viewCount(realTimeViewCount)
                .likeCount(portfolio.getLikeCount())
                .createdAt(portfolio.getCreatedAt())
                .lastModifiedAt(portfolio.getLastModifiedAt())
                .build();
    }
}