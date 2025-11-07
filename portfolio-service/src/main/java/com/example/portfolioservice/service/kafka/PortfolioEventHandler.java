package com.example.portfolioservice.service.kafka;

import com.example.portfolioservice.config.KafkaTopics;
import com.example.portfolioservice.dto.event.UserProfileUpdatedEvent;
import com.example.portfolioservice.dto.event.UserRegistrationRequestedEvent;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioEventHandler {

    private final PortfolioRepository portfolioRepository;

    /**
     * [SAGA] 회원가입 완료 시, 비어있는 포트폴리오 생성 (지연 생성)
     */
    @Transactional
    @KafkaListener(topics = KafkaTopics.USER_REGISTRATION_REQUESTED, groupId = "portfolio-consumer-group")
    public void handleUserRegistration(UserRegistrationRequestedEvent event) {
        log.info("[Kafka] 회원가입 이벤트 수신. UserId: {}", event.getUserId());

        // 멱등성 보장
        if (portfolioRepository.existsById(event.getUserId())) {
            log.warn("이미 포트폴리오가 존재함 (멱등성). UserId: {}", event.getUserId());
            return;
        }

        try {
            PortfolioEntity portfolio = PortfolioEntity.builder()
                    .userId(event.getUserId())
                    .name(event.getName())
                    .email(event.getEmail())
                    .birthdate(event.getBirthdate())
                    .gender(event.getGender())
                    // photoUrl, oneLiner, content는 null (사용자가 채워야 함)
                    .build();

            portfolioRepository.save(portfolio);
            log.info("기본 포트폴리오 생성 완료. UserId: {}", event.getUserId());

        } catch (DataIntegrityViolationException e) {
            log.warn("DB 제약조건 위반 (아마도 중복 처리됨). UserId: {}", event.getUserId(), e);
        } catch (Exception e) {
            log.error("기본 포트폴리오 생성 중 알 수 없는 오류. UserId: {}", event.getUserId(), e);
            // 이 서비스는 SAGA의 최종 단계이므로, 실패해도 롤백 이벤트를 발행하지 않음.
        }
    }

    /**
     * [Event] user-service에서 프로필 변경 시, 캐시된 데이터 동기화
     */
    @Transactional
    @KafkaListener(topics = KafkaTopics.USER_PROFILE_UPDATED, groupId = "portfolio-consumer-group")
    public void handleUserProfileUpdate(UserProfileUpdatedEvent event) {
        log.info("[Kafka] 프로필 업데이트 이벤트 수신. UserId: {}", event.getUserId());

        Optional<PortfolioEntity> optionalPortfolio = portfolioRepository.findById(event.getUserId());

        if (optionalPortfolio.isPresent()) {
            PortfolioEntity portfolio = optionalPortfolio.get();
            portfolio.updateCache(
                    event.getName(),
                    event.getEmail(),
                    event.getBirthdate(),
                    event.getGender()
            );
            portfolioRepository.save(portfolio);
            log.info("포트폴리오 캐시(DB) 동기화 완료. UserId: {}", event.getUserId());
        } else {
            // 회원가입 이벤트가 누락되었거나, 이 이벤트가 먼저 도착한 경우
            log.warn("업데이트할 포트폴리오가 존재하지 않음. UserId: {}", event.getUserId());
            // (필요시) 여기서 기본 포트폴리오를 생성하는 로직을 추가할 수도 있음.
        }
    }
}