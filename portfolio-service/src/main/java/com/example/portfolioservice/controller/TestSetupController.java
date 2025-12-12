package com.example.portfolioservice.controller;

import com.example.portfolioservice.entity.PortfolioEntity;
// import com.example.portfolioservice.entity.enumerate.Template; // 삭제됨
import com.example.portfolioservice.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/portfolio/test")
@RequiredArgsConstructor
// @Profile({"dev", "test"})
public class TestSetupController {

    private final PortfolioRepository portfolioRepository;

    private static final String[] POSITIONS = {
            "BACKEND", "FRONTEND", "FULLSTACK", "DEVOPS", "AI_ML", "MOBILE", "DESIGN", "PM"
    };

    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<Void> setupPortfolio(@RequestBody Map<String, Object> request) {

        Long userId = ((Number) request.get("userId")).longValue();

        // 1. Upsert 로직 (있으면 가져오고, 없으면 새로 만든다)
        PortfolioEntity portfolio = portfolioRepository.findByUserId(userId)
                .orElseGet(() -> PortfolioEntity.builder()
                        .userId(userId)
                        .name("Test User " + userId)
                        .email("test" + userId + "@example.com")
                        .birthdate("2000-01-01")
                        .gender(null)
                        .isPublished(true) //  즉시 공개 상태로 생성
                        .build());

        String randomPosition = POSITIONS[(int) (userId % POSITIONS.length)];

        // 2. 테스트용 데이터 강제 주입 (Update)
        portfolio.updateUserInput(
                "https://via.placeholder.com/150",
                "안녕하세요, 성능 테스트용 포트폴리오입니다.",
                "이것은 k6 테스트를 위해 자동 생성된 포트폴리오 내용입니다.",
                randomPosition,
                null
        );

        portfolioRepository.save(portfolio);
        log.info("테스트용 포트폴리오 생성 완료: userId={}", userId);

        return ResponseEntity.ok().build();
    }
}