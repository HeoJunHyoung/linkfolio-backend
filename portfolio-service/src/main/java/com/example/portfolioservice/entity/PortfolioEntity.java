package com.example.portfolioservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import com.example.commonmodule.entity.enumerate.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "`portfolio`", indexes = {
        @Index(name = "idx_portfolio_popularity", columnList = "is_published, popularity_score")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId; // 2. 소유자를 나타내는 'userId' 컬럼 (Unique 제약조건으로 1:1 유지)

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

    @Column(name = "hashtags")
    private String hashtags;

    @Lob // TEXT 타입
    @Column(name = "content")
    private String content; // 포트폴리오 내용 (PR 등)

    @Column(name = "position")
    private String position; // 포지션 (예: 프론트엔드 / 백엔드)

    // --- 포트폴리오 상태 관리 ---
    @Column(name = "is_published", nullable = false)
    @ColumnDefault("false") // DB 기본값
    private boolean isPublished = false; // JPA 기본값

    @Column(name = "view_count", nullable = false)
    @ColumnDefault("0")
    private Long viewCount = 0L;

    @Column(name = "like_count", nullable = false)
    @ColumnDefault("0")
    private Long likeCount = 0L; // 북마크 수

    @Column(name = "popularity_score")
    @ColumnDefault("0")
    private Long popularityScore = 0L;

    // == 생성자 == //
    @Builder
    public PortfolioEntity(Long userId, String name, String email, String birthdate, Gender gender, String photoUrl, String oneLiner, String content, String position, String hashtags, boolean isPublished) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.birthdate = birthdate;
        this.gender = gender;
        this.photoUrl = photoUrl;
        this.oneLiner = oneLiner;
        this.content = content;
        this.position = position;
        this.hashtags = hashtags;
        this.isPublished = isPublished;
        this.viewCount = 0L;
        this.likeCount = 0L;
    }

    // Kafka 이벤트로 캐시된 정보 갱신
    public void updateCache(String name, String email, String birthdate, Gender gender) {
        this.name = name;
        this.email = email;
        this.birthdate = birthdate;
        this.gender = gender;
    }

    // --- 내부 헬퍼 메서드 --- //
    public void increaseViewCount() {
        this.viewCount++;
        calculatePopularityScore();
    }

    public void increaseLikeCount() {
        this.likeCount++;
        calculatePopularityScore();
    }

    public void decreaseLikeCount() {
        this.likeCount = Math.max(0, this.likeCount - 1);
        calculatePopularityScore();
    }

    // 사용자가 입력한 포트폴리오 정보 갱신
    public void updateUserInput(String photoUrl, String oneLiner, String content, String position, List<String> hashtags) {
        this.photoUrl = photoUrl;
        this.oneLiner = oneLiner;
        this.content = content;
        this.position = position;

        if (hashtags == null || hashtags.isEmpty()) {
            this.hashtags = null;
        } else {
            List<String> limitedHashtags = hashtags.stream().limit(4).toList();
            this.hashtags = String.join(",", limitedHashtags);
        }

        // 사용자가 한 번이라도 저장하면 '발행' 상태로 간주
        if (!this.isPublished) {
            this.isPublished = true;
        }
        calculatePopularityScore();
    }

    // 점수 계산 로직
    // ㄴ Hacker News 알고리즘의 변형(시간이 지날수록 분모가 커져서 점수가 낮아지도록) -> Score = (Points) / (Time + 2)^1.5
    private void calculatePopularityScore() {

        long points = (this.viewCount * 1) + (this.likeCount * 50);

        // 2. 시간 경과 계산 (생성일로부터 현재까지의 시간 '시' 단위)
        LocalDateTime timeBase = (this.getLastModifiedAt() != null) ? this.getLastModifiedAt() : LocalDateTime.now();

        // 시간 차이가 음수가 나오지 않도록 방어 로직 (서버 시간차 등 대비)
        long hoursDiff = Math.max(0, ChronoUnit.HOURS.between(timeBase, LocalDateTime.now()));

        // 3. Time Decay 적용 (오래될수록 점수 하락)
        double timeFactor = Math.pow(hoursDiff + 2, 1.5);

        // 4. 최종 점수 계산
        this.popularityScore = (long) ((points * 1000) / timeFactor);
    }


}