package com.example.supportservice.service;

import com.example.commonmodule.exception.BusinessException;
import com.example.supportservice.dto.request.NoticeRequest;
import com.example.supportservice.dto.response.NoticeResponse;
import com.example.supportservice.entity.NoticeEntity;
import com.example.supportservice.exception.ErrorCode;
import com.example.supportservice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
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
    public Page<NoticeResponse> getNotices(Pageable pageable) {
        return noticeRepository.findAll(pageable)
                .map(this::toResponse);
    }

    // [USER/ADMIN] 공지사항 상세 조회
    public NoticeResponse getNotice(Long id) {
        NoticeEntity notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        return toResponse(notice);
    }

    // [ADMIN] 공지사항 등록
    @Transactional
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
    public void updateNotice(Long id, NoticeRequest request) {
        NoticeEntity notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));

        notice.update(request.getTitle(), request.getContent(), request.isImportant());
    }

    // [ADMIN] 공지사항 삭제
    @Transactional
    public void deleteNotice(Long id) {
        noticeRepository.deleteById(id);
    }

    // Mapper Method
    private NoticeResponse toResponse(NoticeEntity entity) {
        return NoticeResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .isImportant(entity.isImportant())
                .createdAt(entity.getCreatedAt().toLocalDate())
                .build();
    }
}