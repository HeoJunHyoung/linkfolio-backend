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
import org.springframework.dao.DataIntegrityViolationException; // [추가] PK 중복 예외
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserServiceClient userServiceClient;
    private final PortfolioMapper portfolioMapper;

    private PortfolioEntity findOrCreateAndHealPortfolio(Long authUserId) {

        // 1. DB에서 내 포트폴리오 조회
        Optional<PortfolioEntity> optionalPortfolio = portfolioRepository.findById(authUserId);

        InternalUserProfileResponse userProfile;

        try {
            // 2. [자가 치유] user-service에서 최신 프로필 정보 조회 (Feign)
            userProfile = userServiceClient.getUserProfile(authUserId);
        } catch (Exception e) {
            // 3-A. [Feign 실패] user-service가 다운됨
            log.error("user-service 호출 실패. 캐시된 데이터로 응답 시도. UserId: {}", authUserId, e);
            // Feign이 실패하면, 이미 DB에 저장된(캐시된) 포트폴리오라도 반환
            // 만약 캐시조차 없다면 (최초방문 + Feign 실패) 에러 발생
            return optionalPortfolio.orElseThrow(
                    () -> new BusinessException(ErrorCode.USER_NOT_FOUND_FEIGN_FAILED));
        }

        // 3-B. [Feign 성공]
        if (optionalPortfolio.isEmpty()) {
            // 4. [지연 생성] DB에 없음 = 마이페이지 첫 방문
            log.info("포트폴리오 없음. user-service 정보로 신규 생성 (isPublished=false). UserId: {}", authUserId);
            PortfolioEntity newPortfolio = PortfolioEntity.builder()
                    .userId(authUserId)
                    .name(userProfile.getName())
                    .email(userProfile.getEmail())
                    .birthdate(userProfile.getBirthdate())
                    .gender(userProfile.getGender())
                    .isPublished(false)
                    .build();

            try {
                // 5. [경쟁 상태 해결] DB 저장 (INSERT)
                // 만약 동시 요청으로 PK 중복 예외가 발생하면 catch 블록으로 이동
                return portfolioRepository.save(newPortfolio);
            } catch (DataIntegrityViolationException e) {
                // 5-B. [경쟁 상태 발생]
                // 다른 요청이 방금 INSERT에 성공했음을 의미 (PK 중복)
                log.warn("경쟁 상태(Race Condition) 감지: 포트폴리오 동시 생성 시도. 기존 레코드 재조회. UserId: {}", authUserId);
                // 이미 생성된 레코드를 다시 조회하여 반환
                return portfolioRepository.findById(authUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)); // 이론상 발생 불가능
            }
        } else {
            // 6. [자가 치유] DB에 있음 = 기존 방문자
            PortfolioEntity portfolio = optionalPortfolio.get();
            // Feign으로 가져온 최신 정보로 캐시(DB) 갱신
            portfolio.updateCache(
                    userProfile.getName(),
                    userProfile.getEmail(),
                    userProfile.getBirthdate(),
                    userProfile.getGender()
            );
            // DB 저장 (UPDATE)
            return portfolioRepository.save(portfolio);
        }
    }


    /**
     * 내 포트폴리오 조회 (마이페이지)
     */
    @Transactional // 쓰기 작업(자가 치유, 지연 생성)이 발생하므로 readOnly=false
    public PortfolioResponse getMyPortfolio(Long authUserId) {
        // 1. 헬퍼 메서드를 호출하여 엔티티를 조회/생성/치유
        PortfolioEntity portfolio = findOrCreateAndHealPortfolio(authUserId);

        // 2. DTO로 변환하여 반환
        return portfolioMapper.toPortfolioResponse(portfolio);
    }

    /**
     * 포트폴리오 생성/수정 (마이페이지 [등록하기] 또는 [수정하기] 버튼)
     */
    @Transactional // 쓰기 작업이므로 readOnly=false
    public PortfolioResponse createOrUpdateMyPortfolio(Long authUserId, PortfolioRequest request) {

        // 1. 헬퍼 메서드를 호출하여 엔티티를 조회/생성/치유 (1차 저장)
        PortfolioEntity portfolio = findOrCreateAndHealPortfolio(authUserId);

        // 2. 사용자 입력 정보(Request)로 엔티티 갱신
        //    updateUserInput 내부에서 isPublished가 'true'로 변경됨
        portfolio.updateUserInput(
                request.getPhotoUrl(),
                request.getOneLiner(),
                request.getContent()
        );

        // 3. DB 저장 (Update) (2차 저장)
        PortfolioEntity updatedPortfolio = portfolioRepository.save(portfolio);

        return portfolioMapper.toPortfolioResponse(updatedPortfolio);
    }

    /**
     * 포트폴리오 카드 목록 조회 (메인 페이지 - 인증 불필요)
     * (변경 없음)
     */
    public Page<PortfolioCardResponse> getPortfolioList(Pageable pageable) {
        // Feign 호출 없이, '발행(isPublished=true)'된 데이터만 조회
        Page<PortfolioEntity> page = portfolioRepository.findAllByIsPublished(true, pageable);
        return page.map(portfolioMapper::toPortfolioCardResponse);
    }

    /**
     * 포트폴리오 상세 조회 (상세보기 - 인증 불필요)
     * (변경 없음)
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