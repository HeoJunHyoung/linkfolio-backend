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
            // 아직 마이페이지에 한 번도 들어오지 않아 포트폴리오가 생성되기 전.
            // (나중에 /me 호출 시 어차피 Feign으로 최신 데이터를 가져와 생성하므로 아무것도 안함)
            log.warn("업데이트할 포트폴리오가 아직 생성되지 않음(Lazy Creation 대기 중). UserId: {}", event.getUserId());
        }
    }
}