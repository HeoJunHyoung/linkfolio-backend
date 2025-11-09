package com.example.portfolioservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import com.example.commonmodule.entity.enumerate.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "`portfolio`")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioEntity extends BaseEntity {

    @Id
    @Column(name = "user_id") // user-service의 PK와 동일
    private Long userId;

    // --- user-service에서 캐싱(비정규화)된 정보 ---
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "birthdate")
    private String birthdate;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    // --- 사용자가 직접 입력하는 정보 ---
    @Column(name = "photo_url")
    private String photoUrl; // 사진

    @Column(name = "one_liner")
    private String oneLiner; // 한 줄 소개

    @Lob // TEXT 타입
    @Column(name = "content")
    private String content; // 포트폴리오 내용 (PR 등)

    // --- 포트폴리오 상태 관리 ---
    @Column(name = "is_published", nullable = false)
    @ColumnDefault("false") // DB 기본값
    private boolean isPublished = false; // JPA 기본값

    @Builder
    public PortfolioEntity(Long userId, String name, String email, String birthdate, Gender gender, String photoUrl, String oneLiner, String content, boolean isPublished) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.birthdate = birthdate;
        this.gender = gender;
        this.photoUrl = photoUrl;
        this.oneLiner = oneLiner;
        this.content = content;
        this.isPublished = isPublished;
    }

    // Kafka 이벤트 또는 Feign으로 캐시된 정보 갱신
    public void updateCache(String name, String email, String birthdate, Gender gender) {
        this.name = name;
        this.email = email;
        this.birthdate = birthdate;
        this.gender = gender;
    }

    // 사용자가 입력한 포트폴리오 정보 갱신
    public void updateUserInput(String photoUrl, String oneLiner, String content) {
        this.photoUrl = photoUrl;
        this.oneLiner = oneLiner;
        this.content = content;

        // 사용자가 한 번이라도 저장하면 '발행' 상태로 간주
        if (!this.isPublished) {
            this.isPublished = true;
        }
    }
}