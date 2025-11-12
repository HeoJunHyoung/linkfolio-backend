// portfolio-service/src/main/java/com/example/portfolioservice/service/kafka/PortfolioEventHandler.java
package com.example.portfolioservice.service.kafka;

import com.example.commonmodule.dto.event.UserProfilePublishedEvent;
import com.example.portfolioservice.config.KafkaTopics;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * [Event] user-service에서 프로필 생성 또는 변경 시, 캐시된 데이터 동기화
     */
    @Transactional
    @KafkaListener(topics = KafkaTopics.USER_PROFILE_UPDATED, groupId = "portfolio-consumer-group")
    public void handleUserProfileUpdate(UserProfilePublishedEvent event) {
        log.info("[Kafka] 프로필 생성/업데이트 이벤트 수신. UserId: {}", event.getUserId());

        Optional<PortfolioEntity> optionalPortfolio = portfolioRepository.findByUserId(event.getUserId());

        if (optionalPortfolio.isPresent()) {
            // [1] Update
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
            // [2] Create: 회원가입 직후(SAGA 완료) 발생한 이벤트.
            PortfolioEntity newPortfolio = PortfolioEntity.builder()
                    .userId(event.getUserId())
                    .name(event.getName())
                    .email(event.getEmail())
                    .birthdate(event.getBirthdate())
                    .gender(event.getGender())
                    .isPublished(false) // [중요] 초기 상태는 false
                    .build();
            portfolioRepository.save(newPortfolio);
            log.info("신규 포트폴리오 초기 레코드 생성 완료. UserId: {}", event.getUserId());
        }
    }
}