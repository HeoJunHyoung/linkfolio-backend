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

import java.util.Optional; // Optional 임포트

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
     */
    @Transactional
    public PortfolioResponse getMyPortfolio(Long authUserId) {

        // 1. DB에서 내 포트폴리오 조회
        Optional<PortfolioEntity> optionalPortfolio = portfolioRepository.findById(authUserId);

        PortfolioEntity portfolio;
        InternalUserProfileResponse userProfile;

        try {
            // 2. [자가 치유] user-service에서 최신 프로필 정보 조회 (Feign)
            userProfile = userServiceClient.getUserProfile(authUserId);
        } catch (Exception e) {
            // 3-A. [Feign 실패] user-service가 다운됨
            log.error("user-service 호출 실패. 캐시된 데이터로 응답. UserId: {}", authUserId, e);
            // Feign이 실패하면, 이미 DB에 저장된(캐시된) 포트폴리오라도 반환
            // 만약 캐시조차 없다면 (최초방문 + Feign 실패) 에러 발생
            return portfolioMapper.toPortfolioResponse(optionalPortfolio.orElseThrow(
                    () -> new BusinessException(ErrorCode.USER_NOT_FOUND_FEIGN_FAILED)));
        }

        if (optionalPortfolio.isEmpty()) {
            // 3-B. [지연 생성] DB에 없음 = 마이페이지 첫 방문
            log.info("포트폴리오 없음. user-service 정보로 신규 생성 (isPublished=false). UserId: {}", authUserId);
            portfolio = PortfolioEntity.builder()
                    .userId(authUserId)
                    .name(userProfile.getName())
                    .email(userProfile.getEmail())
                    .birthdate(userProfile.getBirthdate())
                    .gender(userProfile.getGender())
                    .isPublished(false) // [중요] 비공개(미발행) 상태로 생성
                    .build();
        } else {
            // 3-C. [자가 치유] DB에 있음 = 기존 방문자
            portfolio = optionalPortfolio.get();
            // Feign으로 가져온 최신 정보로 캐시(DB) 갱신
            portfolio.updateCache(
                    userProfile.getName(),
                    userProfile.getEmail(),
                    userProfile.getBirthdate(),
                    userProfile.getGender()
            );
        }

        // 4. DB 저장 (INSERT 또는 UPDATE)
        PortfolioEntity savedPortfolio = portfolioRepository.save(portfolio);

        // 5. DTO로 변환하여 반환 (isPublished 값 포함)
        return portfolioMapper.toPortfolioResponse(savedPortfolio);
    }

    /**
     * 포트폴리오 생성/수정 (마이페이지 [등록하기] 또는 [수정하기] 버튼)
     */
    @Transactional
    public PortfolioResponse createOrUpdateMyPortfolio(Long authUserId, PortfolioRequest request) {

        // 1.getMyPortfolio를 먼저 호출하여 레코드를 보장(지연 생성)하고, 최신 캐시를 반영(자가 치유).
        this.getMyPortfolio(authUserId);

        // 2. getMyPortfolio가 레코드를 생성/갱신했으므로, 이제 DB에서 조회
        PortfolioEntity portfolio = portfolioRepository.findById(authUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)); // 로직상 발생 불가능

        // 3. 사용자 입력 정보(Request)로 엔티티 갱신
        //    updateUserInput 내부에서 isPublished가 'true'로 변경됨
        portfolio.updateUserInput(
                request.getPhotoUrl(),
                request.getOneLiner(),
                request.getContent()
        );

        // 4. DB 저장 (Update)
        PortfolioEntity updatedPortfolio = portfolioRepository.save(portfolio);

        return portfolioMapper.toPortfolioResponse(updatedPortfolio);
    }

    /**
     * 포트폴리오 카드 목록 조회 (메인 페이지 - 인증 불필요)
     */
    public Page<PortfolioCardResponse> getPortfolioList(Pageable pageable) {
        // Feign 호출 없이, '발행(isPublished=true)'된 데이터만 조회
        Page<PortfolioEntity> page = portfolioRepository.findAllByIsPublished(true, pageable);
        return page.map(portfolioMapper::toPortfolioCardResponse);
    }

    /**
     * 포트폴리오 상세 조회 (상세보기 - 인증 불필요)
     */
    public PortfolioResponse getPortfolioDetails(Long userId) {
        // Feign 호출 없이, 캐시된 DB 데이터만으로 응답
        PortfolioEntity portfolio = portfolioRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // 발행(isPublished=true)되지 않은 포트폴리오는 찾을 수 없는 것으로 처리
        if (!portfolio.isPublished()) {
            log.warn("아직 발행되지 않은 포트폴리오 접근 시도. UserId: {}", userId);
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        return portfolioMapper.toPortfolioResponse(portfolio);
    }
}