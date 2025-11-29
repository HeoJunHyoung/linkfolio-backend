package com.example.communityservice.entity;

import com.example.commonmodule.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_bookmark",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_user_bookmark", columnNames = {"post_id", "user_id"})
        }, indexes = {
                @Index(name = "idx_bookmark_user_date", columnList = "user_id, created_at DESC") // 내 북마크 목록 조회용 (userId로 조회 + 최신순 정렬)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostBookmarkEntity extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public PostBookmarkEntity(PostEntity post, Long userId) {
        this.post = post;
        this.userId = userId;
    }
}