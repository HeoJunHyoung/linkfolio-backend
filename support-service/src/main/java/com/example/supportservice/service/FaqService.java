package com.example.supportservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.supportservice.dto.request.FaqRequest;
import com.example.supportservice.dto.response.FaqResponse;
import com.example.supportservice.entity.FaqEntity;
import com.example.supportservice.entity.enumerate.FaqCategory;
import com.example.supportservice.exception.ErrorCode;
import com.example.supportservice.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;

    // 자주 묻는 질문 조회 (title과 contents를 모두 FaqResponse에 담아서 반환 = 목록 조회, 상세 조회 구분 없음)
    @Cacheable(value = "faqs", key = "'all'")
    public List<FaqResponse> getAllFaqs() {
        return faqRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "faqs", key = "#category")
    public List<FaqResponse> getFaqsByCategory(FaqCategory category) {
        return faqRepository.findAllByCategory(category).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 자주 묻는 질문 생성
    @Transactional
    @CacheEvict(value = "faqs", allEntries = true)
    public void createFaq(FaqRequest request) {
        FaqEntity faq = FaqEntity.builder()
                .category(request.getCategory())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .build();
        faqRepository.save(faq);
    }

    // 자주 묻는 질문 수정
    @Transactional
    @CacheEvict(value = "faqs", allEntries = true)
    public void updateFaq(Long id, FaqRequest request) {
        FaqEntity faq = faqRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
        faq.update(request.getCategory(), request.getQuestion(), request.getAnswer());
    }

    // 자주 묻는 질문 삭제
    @Transactional
    @CacheEvict(value = "faqs", allEntries = true)
    public void deleteFaq(Long id) {
        faqRepository.deleteById(id);
    }

    private FaqResponse toResponse(FaqEntity entity) {
        return FaqResponse.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .question(entity.getQuestion())
                .answer(entity.getAnswer())
                .build();
    }
}