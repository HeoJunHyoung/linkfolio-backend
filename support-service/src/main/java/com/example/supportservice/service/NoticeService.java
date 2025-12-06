package com.example.supportservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.supportservice.dto.request.NoticeRequest;
import com.example.supportservice.dto.response.CustomPageResponse;
import com.example.supportservice.dto.response.NoticeListResponse;
import com.example.supportservice.dto.response.NoticeResponse;
import com.example.supportservice.entity.NoticeEntity;
import com.example.supportservice.exception.ErrorCode;
import com.example.supportservice.repository.NoticeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String NOTICE_LIST_KEY_PREFIX = "noticeList::";
    private static final String NOTICE_DETAIL_KEY_PREFIX = "noticeDetail::";


    /**
     * [USER/ADMIN] 공지사항 목록 조회
     */
    public Page<NoticeListResponse> getNotices(Pageable pageable) {
        String cacheKey = NOTICE_LIST_KEY_PREFIX + pageable.getPageNumber() + "-" + pageable.getPageSize();

        // 1. 캐시 조회
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                CustomPageResponse<NoticeListResponse> customPage = objectMapper.readValue(
                        cachedJson,
                        new TypeReference<CustomPageResponse<NoticeListResponse>>() {}
                );
                return customPage.toPage();
            } catch (JsonProcessingException e) {
                log.error("Cache Deserialization Failed", e);
            }
        }

        // 2. DB 조회
        Page<NoticeListResponse> page = noticeRepository.findAll(pageable).map(this::toListResponse);

        // 3. 캐시 저장
        try {
            CustomPageResponse<NoticeListResponse> wrapper = new CustomPageResponse<>(page);
            String jsonToCache = objectMapper.writeValueAsString(wrapper);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Cache Serialization Failed", e);
        }

        return page;
    }


    /**
     * [USER/ADMIN] 공지사항 상세 조회
     */
    public NoticeResponse getNotice(Long id) {
        String cacheKey = NOTICE_DETAIL_KEY_PREFIX + id;

        // 1. 캐시 조회
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, NoticeResponse.class);
            } catch (JsonProcessingException e) {
                log.error("Cache Deserialization Failed", e);
            }
        }

        // 2. DB 조회
        NoticeEntity notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        NoticeResponse response = toResponse(notice);

        // 3. 캐시 저장
        try {
            String jsonToCache = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonToCache, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Cache Serialization Failed", e);
        }

        return response;
    }


    /**
     * [ADMIN] 공지사항 등록
     */
    @Transactional
    public void createNotice(NoticeRequest request) {
        NoticeEntity notice = NoticeEntity.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .isImportant(request.isImportant())
                .build();
        noticeRepository.save(notice);

        // 목록 캐시 전체 초기화
        clearNoticeListCache();
    }


    /**
     * [ADMIN] 공지사항 수정
     */
    @Transactional
    public void updateNotice(Long id, NoticeRequest request) {
        NoticeEntity notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));

        notice.update(request.getTitle(), request.getContent(), request.isImportant());

        // 1. 해당 상세 캐시 삭제
        redisTemplate.delete(NOTICE_DETAIL_KEY_PREFIX + id);
        // 2. 목록 캐시 전체 초기화 (제목 등이 변경되어 정렬/내용에 영향)
        clearNoticeListCache();
    }


    /**
     * [ADMIN] 공지사항 삭제
     */
    @Transactional
    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);

        // 1. 해당 상세 캐시 삭제
        redisTemplate.delete(NOTICE_DETAIL_KEY_PREFIX + id);
        // 2. 목록 캐시 전체 초기화
        clearNoticeListCache();
    }


    //========================//
    //==== Helper Methods ====//
    //========================//

    /**
     * 공지사항 목록 캐시 일괄 삭제 ("noticeList::*" 패턴을 가진 모든 키 삭제)
     */
    private void clearNoticeListCache() {
        Set<String> keys = redisTemplate.keys(NOTICE_LIST_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} notice list cache keys.", keys.size());
        }
    }

    private NoticeResponse toResponse(NoticeEntity entity) {
        return NoticeResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .isImportant(entity.isImportant())
                .createdAt(entity.getCreatedAt().toLocalDate())
                .build();
    }

    private NoticeListResponse toListResponse(NoticeEntity entity) {
        return NoticeListResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .isImportant(entity.isImportant())
                .createdAt(entity.getCreatedAt().toLocalDate())
                .build();
    }
}