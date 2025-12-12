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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioLikeRepository portfolioLikeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 캐싱 - 포트폴리오 상세 조회 Key 규칙
    // ㄴ 1) "portfolio:details:{id}" (정적)
    // ㄴ 2) "portfolio:stats:{id}" (동적)
    private static final String STATIC_KEY_PREFIX = "portfolio:details:";
    private static final String STATS_KEY_PREFIX = "portfolio:stats:";

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
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        portfolio.updateUserInput(request.getPhotoUrl(), request.getOneLiner(), request.getContent(), request.getPosition(), request.getHashtags());
        PortfolioEntity updatedPortfolio = portfolioRepository.save(portfolio);

        // 내용이 수정되었으므로 정적 캐시 삭제 (Eviction)
        // ㄴ 다음 조회 시 DB에서 새 내용을 가져와 캐싱함. (조회수/좋아요는 statsKey에 있으므로 유지됨)
        redisTemplate.delete(STATIC_KEY_PREFIX + updatedPortfolio.getPortfolioId());

        return portfolioMapper.toPortfolioResponse(updatedPortfolio, false);
    }

    /**
     * 포트폴리오 카드 목록 조회 (메인 페이지 - 인증 불필요)
     */
    public Slice<PortfolioCardResponse> getPortfolioList(Long userId, Pageable pageable, String position) {
        return portfolioRepository.searchPortfolioList(userId, position, pageable);
    }

    /**
     * 포트폴리오 상세 조회 (Split Strategy: 정적+동적 병합)
     */
    @Transactional
    public PortfolioDetailsResponse getPortfolioDetails(Long portfolioId, AuthUser authUser) {

        String statsKey = STATS_KEY_PREFIX + portfolioId;

        // 1. 조회수(동적 데이터) 증가 (DB 부하 X)
        // ㄴ A. [Display용] 사용자에게 보여줄 실시간 값 (통계용 Hash: portfolio:stats:{id})
        redisTemplate.opsForHash().increment(statsKey, "viewCount", 1L);
        // ㄴ B. [Batch용] DB에 나중에 반영할 증가분 (배치용 Hash: portfolio:views)
        redisTemplate.opsForHash().increment("portfolio:views", String.valueOf(portfolioId), 1L);

        // 2. 정적 데이터 조회 (제목, 내용 등)
        PortfolioDetailsResponse response = getStaticPortfolioData(portfolioId);

        // 3. 동적 데이터 조회 및 병합 (조회수, 북마크 수)
        mergeDynamicStats(portfolioId, response);

        // 4. 개인 데이터(좋아요) 여부 (이건 캐싱 불가능해서 DB 조회)
        boolean isLiked = false;
        if (authUser != null) {
            // 성능 최적화: ID만으로 조회 (Entity 조회 X)
            PortfolioEntity proxy = portfolioRepository.getReferenceById(portfolioId);
            isLiked = portfolioLikeRepository.existsByLikerIdAndPortfolio(authUser.getUserId(), proxy);
        }

        response.setLiked(isLiked);

        return response;
    }

    //============================//
    //== Internal Helper Method ==//
    //============================//

    // 정적 데이터(title, content, ...)를 Redis에서 조회
    private PortfolioDetailsResponse getStaticPortfolioData(Long portfolioId) {
        String cacheKey = STATIC_KEY_PREFIX + portfolioId;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        // Cache Hit -> Redis 조회
        if (StringUtils.hasText(cachedJson)) {
            try {
                return objectMapper.readValue(cachedJson, PortfolioDetailsResponse.class);
            } catch (JsonProcessingException e) {
                log.error("JSON 파싱 에러. DB에서 다시 조회합니다.", e);
            }
        }

        // Cache Miss -> DB 조회
        PortfolioEntity portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if (!portfolio.isPublished()) {
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        PortfolioDetailsResponse response = portfolioMapper.toPortfolioResponse(portfolio, false);

        // Redis 저장 (TTL 1시간)
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Redis 저장 실패", e);
        }

        return response;
    }

    private void mergeDynamicStats(Long portfolioId, PortfolioDetailsResponse response) {
        String statsKey = STATS_KEY_PREFIX + portfolioId;
        List<Object> stats = redisTemplate.opsForHash().multiGet(statsKey, Arrays.asList("viewCount", "likeCount"));

        Object viewCountObj = stats.get(0);
        Object likeCountObj = stats.get(1);

        // Redis에 통계 데이터가 없는 경우 (Cache Miss or First Access)
        // ㄴ DB에 있는 최신 값을 가져와서 Redis를 초기화해줘야 함 (동기화)
        if (viewCountObj == null || likeCountObj == null) {
            // 정적 데이터 조회 시 사용했던 Entity 정보(response)에 있는 값을 기준으로 초기화
            // ㄴ Redis가 비어있다면 DB 기준으로 Redis에 재설정
            redisTemplate.opsForHash().putIfAbsent(statsKey, "viewCount", String.valueOf(response.getViewCount()));
            redisTemplate.opsForHash().putIfAbsent(statsKey, "likeCount", String.valueOf(response.getLikeCount()));
            // 다시 읽거나, 현재 값 사용
            if (viewCountObj != null) response.setViewCount(Long.parseLong(viewCountObj.toString()));
            if (likeCountObj != null) response.setLikeCount(Long.parseLong(likeCountObj.toString()));
        } else {
            // Redis 값 적용
            response.setViewCount(Long.parseLong(viewCountObj.toString()));
            response.setLikeCount(Long.parseLong(likeCountObj.toString()));
        }
    }

}