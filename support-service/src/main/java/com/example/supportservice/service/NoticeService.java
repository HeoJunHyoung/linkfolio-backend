package com.example.supportservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.supportservice.dto.request.NoticeRequest;
import com.example.supportservice.dto.response.NoticeListResponse;
import com.example.supportservice.dto.response.NoticeResponse;
import com.example.supportservice.entity.NoticeEntity;
import com.example.supportservice.exception.ErrorCode;
import com.example.supportservice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    // [USER/ADMIN] 공지사항 목록 조회
    @Cacheable(value = "noticeList", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<NoticeListResponse> getNotices(Pageable pageable) {
        return noticeRepository.findAll(pageable)
                .map(this::toListResponse);
    }

    // [USER/ADMIN] 공지사항 상세 조회
    @Cacheable(value = "noticeDetail", key = "#id")
    public NoticeResponse getNotice(Long id) {
        NoticeEntity notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        return toResponse(notice);
    }

    // [ADMIN] 공지사항 등록
    @Transactional
    @CacheEvict(value = "noticeList", allEntries = true) // 목록 캐시만 초기화
    public void createNotice(NoticeRequest request) {
        NoticeEntity notice = NoticeEntity.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .isImportant(request.isImportant())
                .build();
        noticeRepository.save(notice);
    }

    // [ADMIN] 공지사항 수정
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "noticeDetail", key = "#id"),    // 해당 상세 캐시 삭제
            @CacheEvict(value = "noticeList", allEntries = true) // 목록 캐시 전체 초기화 (제목 등이 바뀔 수 있으므로)
    })
    public void updateNotice(Long id, NoticeRequest request) {
        NoticeEntity notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));

        notice.update(request.getTitle(), request.getContent(), request.isImportant());
    }

    // [ADMIN] 공지사항 삭제
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "noticeDetail", key = "#id"),
            @CacheEvict(value = "noticeList", allEntries = true)
    })
    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);
    }


    // 상세 조회용 매퍼 (Content 포함)
    private NoticeResponse toResponse(NoticeEntity entity) {
        return NoticeResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .isImportant(entity.isImportant())
                .createdAt(entity.getCreatedAt().toLocalDate())
                .build();
    }

    // 목록 조회용 매퍼 (Content 제외)
    private NoticeListResponse toListResponse(NoticeEntity entity) {
        return NoticeListResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .isImportant(entity.isImportant())
                .createdAt(entity.getCreatedAt().toLocalDate())
                .build();
    }
}