package com.example.supportservice.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Getter
@NoArgsConstructor // Redis 역직렬화를 위해 필수!
public class CustomPageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;

    // Page 객체를 받아서 이 객체(CustomPageResponse)로 변환하는 생성자
    public CustomPageResponse(Page<T> page) {
        this.content = page.getContent();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
        this.totalElements = page.getTotalElements();
    }

    // 다시 Page 인터페이스 객체로 복구하는 메서드
    public Page<T> toPage() {
        return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), totalElements);
    }
}