package com.example.supportservice.controller;

import com.example.supportservice.entity.FaqEntity;
import com.example.supportservice.entity.NoticeEntity;
import com.example.supportservice.entity.enumerate.FaqCategory;
import com.example.supportservice.repository.FaqRepository;
import com.example.supportservice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/support/test")
@RequiredArgsConstructor
// @Profile({"dev", "test"})
public class TestSetupController {

    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;

    /**
     * 공지사항 및 FAQ 기본 데이터 생성
     * 파라미터 없음 (호출 시 정해진 개수만큼 생성)
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<Void> setupSupportData() {
        log.info("고객센터 더미 데이터 생성 시작");

        // 1. 공지사항 30개 생성
        List<NoticeEntity> notices = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            notices.add(NoticeEntity.builder()
                    .title("[필독] 성능 테스트 공지사항 " + i)
                    .content("공지사항 내용입니다. 테스트 중입니다.")
                    .isImportant(true)
                    .build());
        }
        noticeRepository.saveAll(notices);

        // 2. FAQ 20개 생성
        List<FaqEntity> faqs = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            faqs.add(FaqEntity.builder()
                    .question("자주 묻는 질문 Q" + i)
                    .answer("답변입니다 A" + i)
                    .category(FaqCategory.ACCOUNT) // 카테고리 고정
                    .build());
        }
        faqRepository.saveAll(faqs);

        log.info("고객센터 데이터(Notice 20, FAQ 20) 생성 완료");
        return ResponseEntity.ok().build();
    }
}