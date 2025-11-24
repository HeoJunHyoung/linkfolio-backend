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
@Table(name = "community_post")
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

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Long viewCount = 0L;

    @Column(nullable = false)
    @ColumnDefault("0")
    private Long bookmarkCount = 0L;

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

    public void increaseViewCount() {
        this.viewCount++;
    }

    // 북마크 증가
    public void increaseBookmarkCount() {
        this.bookmarkCount++;
    }

    // 북마크 감소
    public void decreaseBookmarkCount() {
        this.bookmarkCount = Math.max(0, this.bookmarkCount - 1);
    }

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