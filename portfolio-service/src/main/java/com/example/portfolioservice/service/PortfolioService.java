package com.example.portfolioservice.service;

import com.example.portfolioservice.client.UserServiceClient;
import com.example.portfolioservice.client.dto.InternalUserProfileResponse;
import com.example.portfolioservice.dto.request.PortfolioRequest;
import com.example.portfolioservice.dto.response.PortfolioCardResponse;
import com.example.portfolioservice.dto.response.PortfolioResponse;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.exception.BusinessException;
import com.example.portfolioservice.exception.ErrorCode;
import com.example.portfolioservice.repository.PortfolioRepository;
import com.example.portfolioservice.util.PortfolioMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserServiceClient userServiceClient;
    private final PortfolioMapper portfolioMapper;

    /**
     * 내 포트폴리오 조회 (마이페이지)
     * (데이터 자가 치유: Feign으로 최신 정보 갱신)
     */
    @Transactional
    public PortfolioResponse getMyPortfolio(Long authUserId) {
        // 1. DB에서 내 포트폴리오 조회
        PortfolioEntity portfolio = findPortfolioById(authUserId);

        try {
            // 2. [자가 치유] user-service에서 최신 프로필 정보 조회
            InternalUserProfileResponse userProfile = userServiceClient.getUserProfile(authUserId);

            // 3. 캐시된 정보(DB) 갱신
            portfolio.updateCache(
                    userProfile.getName(),
                    userProfile.getEmail(),
                    userProfile.getBirthdate(),
                    userProfile.getGender()
            );
            portfolioRepository.save(portfolio); // 변경 감지

        } catch (Exception e) {
            // Feign 실패 시, 일단 DB에 있는 데이터로 응답 (장애 격리)
            log.error("user-service 호출 실패. 캐시된 데이터로 응답. UserId: {}", authUserId, e);
        }

        // 4. DTO로 변환하여 반환
        return portfolioMapper.toPortfolioResponse(portfolio);
    }

    /**
     * 포트폴리오 생성/수정 (마이페이지)
     */
    @Transactional
    public PortfolioResponse createOrUpdateMyPortfolio(Long authUserId, PortfolioRequest request) {
        // 1. DB에서 내 포트폴리오 조회
        PortfolioEntity portfolio = findPortfolioById(authUserId);

        // 2. 사용자 입력 정보(Request)로 엔티티 갱신
        portfolio.updateUserInput(
                request.getPhotoUrl(),
                request.getOneLiner(),
                request.getContent()
        );

        // 3. DB 저장
        PortfolioEntity updatedPortfolio = portfolioRepository.save(portfolio);

        return portfolioMapper.toPortfolioResponse(updatedPortfolio);
    }

    /**
     * 포트폴리오 카드 목록 조회 (메인 페이지 - 인증 불필요)
     */
    public Page<PortfolioCardResponse> getPortfolioList(Pageable pageable) {
        // [성능] Feign 호출 없이, 캐시된 DB 데이터만으로 응답
        Page<PortfolioEntity> page = portfolioRepository.findAll(pageable);
        return page.map(portfolioMapper::toPortfolioCardResponse);
    }

    /**
     * 포트폴리오 상세 조회 (상세보기 - 인증 불필요)
     */
    public PortfolioResponse getPortfolioDetails(Long userId) {
        // [성능] Feign 호출 없이, 캐시된 DB 데이터만으로 응답
        PortfolioEntity portfolio = findPortfolioById(userId);
        return portfolioMapper.toPortfolioResponse(portfolio);
    }

    // --- 내부 헬퍼 메서드 ---
    private PortfolioEntity findPortfolioById(Long userId) {
        return portfolioRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
    }
}