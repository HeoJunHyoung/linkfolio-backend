package com.example.portfolioservice.service.kafka;

import com.example.commonmodule.entity.enumerate.Gender;
import com.example.portfolioservice.entity.PortfolioEntity;
import com.example.portfolioservice.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord; // [추가]
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioEventHandler {

    private final PortfolioRepository portfolioRepository;

    @Transactional
    @KafkaListener(topics = "user_db_server.user_db.user_profile", groupId = "portfolio-consumer-group")
    public void handleUserProfileUpdate(ConsumerRecord<Object, Object> record) {
        if (record.value() == null) return;

        GenericRecord value = (GenericRecord) record.value();

        // Avro 데이터 파싱 (null 체크 포함)
        Long userId = (Long) value.get("user_id");
        String name = value.get("name").toString();
        String email = value.get("email").toString();
        String birthdate = value.get("birthdate") != null ? value.get("birthdate").toString() : null;

        Gender gender = null;
        if (value.get("gender") != null) {
            try {
                gender = Gender.valueOf(value.get("gender").toString());
            } catch (Exception e) {}
        }

        log.info("[CDC 수신] 프로필 변경 감지. UserId: {}", userId);

        Optional<PortfolioEntity> optionalPortfolio = portfolioRepository.findByUserId(userId);

        if (optionalPortfolio.isPresent()) {
            // [Update]
            PortfolioEntity portfolio = optionalPortfolio.get();
            portfolio.updateCache(name, email, birthdate, gender);
            // portfolioRepository.save(portfolio);
        } else {
            // [Create]
            PortfolioEntity newPortfolio = PortfolioEntity.builder()
                    .userId(userId)
                    .name(name)
                    .email(email)
                    .birthdate(birthdate)
                    .gender(gender)
                    .isPublished(false)
                    .build();
            portfolioRepository.save(newPortfolio);
        }
    }
}