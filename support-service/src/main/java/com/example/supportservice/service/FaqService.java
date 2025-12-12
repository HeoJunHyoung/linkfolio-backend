package com.example.supportservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.supportservice.dto.request.FaqRequest;
import com.example.supportservice.dto.response.FaqResponse;
import com.example.supportservice.entity.FaqEntity;
import com.example.supportservice.entity.enumerate.FaqCategory;
import com.example.supportservice.exception.ErrorCode;
import com.example.supportservice.repository.FaqRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FaqService {

    private final FaqRepository faqRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 캐시 키 상수
    private static final String FAQ_ALL_KEY = "faqs::all";
    private static final String FAQ_CATEGORY_KEY_PREFIX = "faqs::";

    /**
     * 전체 FAQ 조회 (수동 캐싱 적용)
     */
    public List<FaqResponse> getAllFaqs() {
        String cacheKey = FAQ_ALL_KEY;

        // 1. 캐시 조회
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<FaqResponse>>() {});
            } catch (JsonProcessingException e) {
                log.error("FAQ Cache Deserialization Failed", e);
            }
        }

        // 2. DB 조회 (캐시가 없거나 에러 발생 시)
        List<FaqResponse> faqs = faqRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // 3. 캐시 저장
        try {
            String jsonToCache = objectMapper.writeValueAsString(faqs);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("FAQ Cache Serialization Failed", e);
        }

        return faqs;
    }

    /**
     * 카테고리별 FAQ 조회 (수동 캐싱 적용)
     */
    public List<FaqResponse> getFaqsByCategory(FaqCategory category) {
        String cacheKey = FAQ_CATEGORY_KEY_PREFIX + category;

        // 1. 캐시 조회 (코드 중복이지만 직관성을 위해 직접 작성)
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<FaqResponse>>() {});
            } catch (JsonProcessingException e) {
                log.error("FAQ Cache Deserialization Failed", e);
            }
        }

        // 2. DB 조회
        List<FaqResponse> faqs = faqRepository.findAllByCategory(category).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // 3. 캐시 저장
        try {
            String jsonToCache = objectMapper.writeValueAsString(faqs);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("FAQ Cache Serialization Failed", e);
        }

        return faqs;
    }

    /**
     * [ADMIN] FAQ 생성
     */
    @Transactional
    public void createFaq(FaqRequest request) {
        FaqEntity faq = FaqEntity.builder()
                .category(request.getCategory())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .build();
        faqRepository.save(faq);
        clearFaqCaches();
    }

    /**
     * [ADMIN] FAQ 수정
     */
    @Transactional
    public void updateFaq(Long id, FaqRequest request) {
        FaqEntity faq = faqRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));
        faq.update(request.getCategory(), request.getQuestion(), request.getAnswer());
        clearFaqCaches();
    }

    /**
     * [ADMIN] FAQ 삭제
     */
    @Transactional
    public void deleteFaq(Long id) {
        faqRepository.deleteById(id);
        clearFaqCaches();
    }


    // 캐시 삭제 헬퍼
    private void clearFaqCaches() {
        Set<String> keys = redisTemplate.keys("faqs::*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} FAQ cache keys.", keys.size());
        }
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