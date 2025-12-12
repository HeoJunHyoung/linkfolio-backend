package com.example.communityservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import com.example.communityservice.entity.enumerate.PostCategory;
import com.example.communityservice.entity.enumerate.RecruitmentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "community_post", indexes = {
        // 1. [QnA 최적화] 카테고리 + 해결여부 + 최신순 (가장 많이 씀)
        @Index(name = "idx_post_qna_date", columnList = "category, is_solved, created_at DESC"),

        // 2. [인기글/공통] 카테고리 + 조회순 (해결여부 무관)
        @Index(name = "idx_post_category_view", columnList = "category, view_count DESC"),

        // 3. [일반 목록] 카테고리 + 최신순 (INFO, RECRUIT 등 해결여부 없는 카테고리용)
        @Index(name = "idx_post_category_date", columnList = "category, created_at DESC"),

        // 4. [마이페이지] 작성자별 조회
        @Index(name = "idx_post_user_id", columnList = "user_id, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostEntity extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 작성자 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostCategory category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Long viewCount = 0L;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Long bookmarkCount = 0L;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Long commentCount = 0L;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean isSolved = false; // QNA 전용

    @Enumerated(EnumType.STRING)
    private RecruitmentStatus recruitmentStatus; // RECRUIT 전용

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostCommentEntity> comments = new ArrayList<>();

    @Builder
    public PostEntity(Long userId, PostCategory category, String title, String content) {
        this.userId = userId;
        this.category = category;
        this.title = title;
        this.content = content;

        if (category == PostCategory.RECRUIT) {
            this.recruitmentStatus = RecruitmentStatus.OPEN;
        }
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    // === 통계 데이터 증감 메서드 ===

    // 조회수 증가 (스케줄러가 사용)
    public void increaseViewCount(Long count) {
        this.viewCount += count;
    }

    // 댓글 수 관리
    public void increaseCommentCount() {
        this.commentCount++;
    }

    public void decreaseCommentCount() {
        this.commentCount = Math.max(0, this.commentCount - 1);
    }

    // 북마크 수 관리
    public void increaseBookmarkCount() {
        this.bookmarkCount++;
    }

    public void decreaseBookmarkCount() {
        this.bookmarkCount = Math.max(0, this.bookmarkCount - 1);
    }

    // 상태 변경 메서드
    public void markAsSolved() {
        if (this.category == PostCategory.QNA) {
            this.isSolved = true;
        }
    }

    public void updateRecruitmentStatus(RecruitmentStatus status) {
        if (this.category == PostCategory.RECRUIT) {
            this.recruitmentStatus = status;
        }
    }
}