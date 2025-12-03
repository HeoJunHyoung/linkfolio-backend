package com.example.supportservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notice", indexes = {
        @Index(name = "idx_notice_important_date", columnList = "is_important DESC, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeEntity extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob // 대용량 텍스트
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean isImportant; // 중요 공지(상단 고정) 여부

    @Builder
    public NoticeEntity(String title, String content, boolean isImportant) {
        this.title = title;
        this.content = content;
        this.isImportant = isImportant;
    }

    public void update(String title, String content, boolean isImportant) {
        this.title = title;
        this.content = content;
        this.isImportant = isImportant;
    }
}